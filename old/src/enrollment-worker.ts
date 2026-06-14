import { Kafka } from 'kafkajs';
import { Pool } from 'pg';
import * as dotenv from 'dotenv';

dotenv.config();

const kafka = new Kafka({
    clientId: 'enrollment-worker',
    brokers: [process.env.KAFKA_BROKER || 'localhost:9092']
});

const consumer = kafka.consumer({ groupId: 'enrollment-group' });
const producer = kafka.producer();

const pool = new Pool({
    user: process.env.POSTGRES_USER || 'postgres',
    password: process.env.POSTGRES_PASSWORD || 'password',
    host: process.env.POSTGRES_HOST || 'localhost',
    database: process.env.POSTGRES_DB || 'enrollment_db',
    port: parseInt(process.env.POSTGRES_PORT || '5432', 10),
});

const isTransientError = (err: any): boolean => {
    if (err && err.code && typeof err.code === 'string') {
        // 08* codes are connection exception in Postgres
        // 53* codes are insufficient resources
        return err.code.startsWith('08') || err.code.startsWith('53') || err.message.includes('connection');
    }
    return false;
};

async function start() {
    await producer.connect();
    await consumer.connect();
    await consumer.subscribe({ topic: 'enrollment_reserved', fromBeginning: true });

    console.log('Worker is running and listening to enrollment_reserved...');

    await consumer.run({
        autoCommit: false, // Explicitly disable autoCommit to manually commit offsets
        eachMessage: async ({ topic, partition, message }) => {
            const payloadString = message.value?.toString();
            if (!payloadString) return;

            console.log(`Received message: ${payloadString}`);
            let payload: { user_id: string; course_id: string; timestamp: string };

            try {
                payload = JSON.parse(payloadString);
            } catch (parseError) {
                console.error('Failed to parse message payload', parseError);
                await consumer.commitOffsets([{ topic, partition, offset: (BigInt(message.offset) + 1n).toString() }]);
                return;
            }

            const { course_id, user_id } = payload;

            try {
                // Try Block (Idempotent UPSERT)
                const query = `
          INSERT INTO enrollments (course_id, user_id)
          VALUES ($1, $2)
          ON CONFLICT (course_id, user_id) DO NOTHING;
        `;
                await pool.query(query, [course_id, user_id]);

                console.log(`Successfully processed enrollment for user ${user_id} in course ${course_id}`);
            } catch (error: any) {
                console.error('Error processing message:', error.message || error);

                if (isTransientError(error)) {
                    // Throwing will cause kafkajs to backoff and retry this message
                    throw error;
                }

                // Catch Block (DLQ Routing)
                try {
                    console.log('Sending non-transient error to DLQ...');
                    await producer.send({
                        topic: 'enrollment_dlq',
                        messages: [
                            {
                                value: JSON.stringify({
                                    original_payload: payload,
                                    error: error.message || error.toString(),
                                    code: error.code
                                })
                            }
                        ]
                    });
                } catch (dlqError) {
                    console.error('Failed to publish to DLQ:', dlqError);
                    // Depending on strictness, we might throw here to prevent data loss.
                    throw dlqError;
                }
            }

            // Commit Offset regardless of success or DLQ routing
            await consumer.commitOffsets([{ topic, partition, offset: (BigInt(message.offset) + 1n).toString() }]);
        },
    });
}

start().catch((err) => {
    console.error('Worker failed to start:', err);
    process.exit(1);
});
