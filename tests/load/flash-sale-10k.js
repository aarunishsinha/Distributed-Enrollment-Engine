import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Configurable via env: k6 run -e PEAK=5000 -e COURSE_ID=CS-102 flash-sale-10k.js
//   MODE=spike      (default) 20% → 50% → PEAK spike, hold, cool down
//   MODE=breakpoint ramp 0 → PEAK over RAMP, abort once p95 > 1s or >1% of requests fail
//   MODE=constant   hold RATE iterations/s for DURATION
// Rates are iterations/s; each iteration sends 2 requests.
const MODE = __ENV.MODE || 'spike';
const PEAK = Number(__ENV.PEAK || 10000);
const RAMP = __ENV.RAMP || '5m';
const RATE = Number(__ENV.RATE || 300);
const DURATION = __ENV.DURATION || '60s';
const COURSE_ID = __ENV.COURSE_ID || 'CS-101';

// 400 (full / already enrolled), 409 (duplicate) and 429 (rate limited) are correct business
// responses, so http_req_failed only counts real failures (5xx, network errors)
http.setResponseCallback(http.expectedStatuses(202, 400, 409, 429));

const SCENARIOS = {
    // Traffic Spike Configuration targeting PEAK iterations/s (default 10K)
    spike: {
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
    // Slow linear ramp to find the throughput knee
    breakpoint: {
        flash_sale_breakpoint: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            preAllocatedVUs: 500,
            maxVUs: 5000,
            stages: [{ target: PEAK, duration: RAMP }],
        },
    },
    // Steady rate to confirm a sustainable throughput
    constant: {
        flash_sale_constant: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 500,
            maxVUs: 5000,
        },
    },
};

if (!SCENARIOS[MODE]) {
    throw new Error(`Unknown MODE "${MODE}" (expected spike, breakpoint or constant)`);
}

// Stop the breakpoint ramp once the system is past its limit
const ABORT_THRESHOLDS = MODE === 'breakpoint' ? {
    http_req_duration: [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '10s' }],
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
} : {};

export const options = {
    scenarios: SCENARIOS[MODE],
    // Dummy thresholds: k6 only reports per-status counts for sub-metrics with a threshold
    thresholds: {
        ...ABORT_THRESHOLDS,
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
