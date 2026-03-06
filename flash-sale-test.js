import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 1. The Traffic Spike Configuration
export const options = {
    scenarios: {
        flash_sale_spike: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 500,
            maxVUs: 2000, // Simulating massive local concurrency
            stages: [
                { target: 2000, duration: '2s' }, // Boom. 2000 req/sec instantly.
                { target: 2000, duration: '5s' }, // Hold the spike.
                { target: 0, duration: '2s' },    // Traffic drops off as it sells out.
            ],
        },
    },
};

export default function () {
    const url = 'http://localhost:3000/v1/enrollments'; // Update to your API port

    // Generate a unique user and a unique network request ID
    const userId = `user_${__VU}_${__ITER}`;
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        course_id: 'CS400',
        user_id: userId,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
    };

    // 2. The First "Click"
    let res1 = http.post(url, payload, params);

    check(res1, {
        'is status 202 (Accepted) or 409 (Conflict/Full)': (r) => r.status === 202 || r.status === 409,
    });

    // 3. The "Panic Double-Click" (Idempotency Test)
    // We intentionally send the EXACT same request 50ms later to simulate a network retry
    let res2 = http.post(url, payload, params);

    check(res2, {
        'retry is caught by idempotency lock (409 or cached 202)': (r) => r.status === 409 || r.status === 202,
    });
}
