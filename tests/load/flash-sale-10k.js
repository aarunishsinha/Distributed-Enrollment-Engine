import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Configurable via env: k6 run -e PEAK=5000 -e COURSE_ID=CS-102 flash-sale-10k.js
const PEAK = Number(__ENV.PEAK || 10000);
const COURSE_ID = __ENV.COURSE_ID || 'CS-101';

// Traffic Spike Configuration targeting PEAK RPS (default 10K)
export const options = {
    scenarios: {
        flash_sale_spike: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 1000,
            maxVUs: 5000,
            stages: [
                { target: Math.round(PEAK * 0.2), duration: '5s' }, // Ramp up to 20% of peak
                { target: Math.round(PEAK * 0.5), duration: '5s' }, // Ramp up to 50% of peak
                { target: PEAK, duration: '10s' },                  // Ramp to peak
                { target: PEAK, duration: '10s' },                  // Hold peak
                { target: 0, duration: '5s' },                      // Cool down
            ],
        },
    },
    // Dummy thresholds: k6 only reports per-status counts for sub-metrics with a threshold
    thresholds: {
        'http_reqs{status:202}': ['count>=0'],
        'http_reqs{status:400}': ['count>=0'],
        'http_reqs{status:409}': ['count>=0'],
        'http_reqs{status:429}': ['count>=0'],
        // Overload signals, to separate them from idempotency failures
        'http_reqs{status:0}': ['count>=0'],   // network errors / timeouts
        'http_reqs{status:500}': ['count>=0'],
        'http_reqs{status:503}': ['count>=0'],
    },
};

export default function () {
    const url = 'http://localhost:8080/api/v1/enrollments';

    // Generate a unique user and a unique request ID
    const userId = `user_${__VU}_${__ITER}`;
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        courseId: COURSE_ID
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
        'double-click rejected (409)': (r) => r.status === 409,
    });
}
