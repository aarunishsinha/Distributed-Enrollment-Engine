import { createClient } from 'redis';
import * as dotenv from 'dotenv';

dotenv.config();

async function init() {
    const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';
    const redis = createClient({ url: redisUrl });

    redis.on('error', (err) => console.error('Redis Client Error', err));

    await redis.connect();
    console.log('Connected to Redis at', redisUrl);

    // Pre-load Redis cache with the course inventory before the API starts accepting traffic
    // SET course:CS400:seats 50
    await redis.set('course:CS400:seats', '50');
    console.log('Successfully set course:CS400:seats to 50');

    await redis.quit();
    console.log('Initialization complete.');
}

init().catch(console.error);
