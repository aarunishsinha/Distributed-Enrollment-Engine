import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Traffic Spike Configuration targeting 10K RPS
export const options = {
    scenarios: {
        flash_sale_spike: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 1000,
            maxVUs: 5000,
            stages: [
                { target: 2000, duration: '5s' },   // Ramp up to 2K RPS
                { target: 5000, duration: '5s' },   // Ramp up to 5K RPS
                { target: 10000, duration: '10s' }, // Peak at 10K RPS
                { target: 10000, duration: '10s' }, // Hold 10K RPS
                { target: 0, duration: '5s' },      // Cool down
            ],
        },
    },
};

export default function () {
    const url = 'http://localhost:8080/api/v1/enrollments';

    // Generate a unique user and a unique request ID
    const userId = `user_${__VU}_${__ITER}`;
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        courseId: 'CS-101'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
            'X-User-Id': userId, // For rate-limiting identification at the gateway
        },
    };

    // First Click
    let res1 = http.post(url, payload, params);

    check(res1, {
        'status is 202 or 400 or 409 or 429': (r) => r.status === 202 || r.status === 400 || r.status === 409 || r.status === 429,
    });

    // Panic Double-Click (Idempotency check)
    // We send the exact same payload and headers immediately after
    let res2 = http.post(url, payload, params);

    check(res2, {
        'retry gets 202, 400, 409, or 429': (r) => r.status === 202 || r.status === 400 || r.status === 409 || r.status === 429,
    });
}
