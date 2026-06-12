import express, { Request, Response } from 'express';
import { createClient } from 'redis';
import { Kafka, Partitioners } from 'kafkajs';
import * as dotenv from 'dotenv';

dotenv.config();

const app = express();
app.use(express.json());

const redisClient = createClient({ url: process.env.REDIS_URL || 'redis://localhost:6379' });
redisClient.on('error', (err) => console.error('Redis error', err));

const kafka = new Kafka({
    clientId: 'enrollment-api',
    brokers: [process.env.KAFKA_BROKER || 'localhost:9092']
});

const producer = kafka.producer({
    createPartitioner: Partitioners.LegacyPartitioner,
    idempotent: true
});

const LUA_ENROLL_SCRIPT = `
local course_seats_key = KEYS[1]
local course_users_key = KEYS[2]
local user_id = ARGV[1]

if redis.call("SISMEMBER", course_users_key, user_id) == 1 then
  return 1 -- User already enrolled
end

local seats = tonumber(redis.call("GET", course_seats_key) or "0")
if seats <= 0 then
  return 2 -- Course full
end

redis.call("DECR", course_seats_key)
redis.call("SADD", course_users_key, user_id)
return 0 -- Success
`;

app.post('/v1/enrollments', async (req: Request, res: Response): Promise<any> => {
    const { user_id, course_id } = req.body;
    const idempotencyKey = req.header('Idempotency-Key');

    if (!idempotencyKey) {
        return res.status(400).json({ error: 'Idempotency-Key header is required' });
    }
    if (!user_id || !course_id) {
        return res.status(400).json({ error: 'user_id and course_id are required' });
    }

    try {
        // 1. Idempotency Check
        const setnxResult = await redisClient.set(`idempotency:${idempotencyKey}`, 'PROCESSING', {
            NX: true,
            EX: 86400
        });

        if (!setnxResult) {
            return res.status(409).json({ error: 'Conflict: Request already processed or in progress' });
        }

        // 2. Concurrency Control (Lua Script)
        const courseSeatsKey = `course:${course_id}:seats`;
        const courseUsersKey = `course:${course_id}:users`;

        const luaResult = await redisClient.eval(LUA_ENROLL_SCRIPT, {
            keys: [courseSeatsKey, courseUsersKey],
            arguments: [user_id]
        });

        if (luaResult === 1) {
            return res.status(400).json({ error: 'User is already enrolled in this course' });
        } else if (luaResult === 2) {
            return res.status(400).json({ error: 'Course is full' });
        }

        // 3. Event Publishing
        const timestamp = new Date().toISOString();
        await producer.send({
            topic: 'enrollment_reserved',
            acks: -1, // acks=all ensures highest durability
            messages: [
                {
                    key: course_id, // course_id as partition key to guarantee sequential processing per course
                    value: JSON.stringify({ user_id, course_id, timestamp })
                }
            ]
        });

        // 4. Response
        return res.status(202).json({ message: 'Accepted' });

    } catch (error) {
        console.error('API Error:', error);
        return res.status(500).json({ error: 'Internal Server Error' });
    }
});

async function start() {
    await redisClient.connect();
    await producer.connect();
    const port = process.env.PORT || 3000;
    app.listen(port, () => {
        console.log(`API Server listening on port ${port}`);
    });
}

start().catch(console.error);
