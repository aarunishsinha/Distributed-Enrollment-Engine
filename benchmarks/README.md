# Flash-Sale Enrollment Benchmark

**Reject path: ~2,500 requests/s sustained** (1,250 users/s, each double-clicking, against a sold-out course) with p95 59–79 ms, 0 errors and zero overselling, on a single laptop with every component in Docker. Throughput collapses between 2,500 and 2,800 requests/s; the limit is CPU in the API gateway and enrollment service.

**Write path:** at the same rate with every first click accepted (1,250 enrollments/s), all 75,000 accepted enrollments reached Postgres, with p95 102 ms, no DLQ entries, and the worker never more than ~0.25 s behind.

## What is measured

One iteration = one user double-clicking "Enroll" on a flash-sale course:

1. `POST /api/v1/enrollments` with a fresh `Idempotency-Key` and a unique `X-User-Id`
2. The same request again immediately (panic double-click), same key and headers

Each request travels:

```
k6 (host) ──► localhost:8080 (Docker Desktop port forward)
  ──► api-gateway        token bucket rate limit (Redis Lua, per X-User-Id)
  ──► enrollment-service
        1. idempotency claim       Redis SETNX idempotency:<key>     → 409 if taken
        2. seat reservation        Redis Lua on course:<id>:seats/users → 400 if full / already enrolled
        3. publish event           Kafka send (not awaited)           → 202
  ◄── response
```

**Latency (`http_req_duration`) = time for the user to get an accept/reject decision.** It excludes connection setup, and it does **not** include persisting the enrollment: the worker writes to Postgres asynchronously from Kafka, outside the measured path. The write-path run covers that leg separately:

```
enrollment_reserved (Kafka, keyed by courseId) ──► enrollment-worker (batches of ≤200)
  ──► Postgres batch UPSERT (ON CONFLICT DO NOTHING) ──► manual offset commit
        failing rows ──► enrollment_dlq
```

Because the partition key is `courseId`, all writes for one course land on a **single partition** and are consumed by one consumer, preserving per-course order.

Two paths are benchmarked, split by whether a request reaches Kafka and Postgres. Both write to Redis on every request:

- **Reject path** (`FLASH-100`, sold out after the first 100 accepts): requests are turned away with 400/409 after Redis-only work.
- **Write path** (`FLASH-BIG`, 100,000 seats): every first click is accepted and travels Redis → Kafka → Postgres.

| Request | Redis writes | Redis reads | Beyond Redis |
|---|---|---|---|
| Any request, at the gateway | rate-limit bucket (`HMSET`, `EXPIRE`) | `HMGET` | |
| First click, course full → 400 | + idempotency key (`SETNX` succeeds) | `SISMEMBER`, `GET` | |
| Duplicate click → 409 | none beyond the rate limit (`SETNX` finds the key taken) | | |
| First click, accepted → 202 | + idempotency key, `DECR` seats, `SADD` user | `SISMEMBER`, `GET` | Kafka publish → worker → Postgres |

Expected responses: `202` accepted, `400` course full / already enrolled, `409` duplicate click, `429` rate limited. Only 5xx and network errors count as failures (`http_req_failed`).

## Setup

| | |
|---|---|
| Machine | MacBook Pro, Intel Core i9-9880H @ 2.30 GHz, 8 cores / 16 threads, 16 GiB RAM |
| OS | macOS 26.6.2 (Darwin 25.6.0, x86_64) |
| Docker | Docker Desktop, 16 CPUs / 7.65 GiB allocated to the VM |
| Load generator | k6 v1.6.1, on the same machine |
| Services | Spring Boot 3.3.0 / Spring Cloud 2023.0.2 on Temurin JDK 21; one instance each of gateway, enrollment-service, worker |
| Data stores | Redis 7, PostgreSQL 15, Kafka (cp-kafka 7.4.0): single broker, `enrollment_reserved` topic with 6 partitions |
| Courses | `FLASH-100`, 100 seats (reject path); `FLASH-BIG`, 100,000 seats (write path). Reset to full before every run |

Full environment capture: [results/env.txt](results/env.txt). All services ran with default JVM, Tomcat and Netty settings.

## How to reproduce

```bash
# Start the stack
docker compose -f docker/docker-compose.yml up -d --build

# Each run.sh call: resets the course, samples docker stats every 1s, runs k6, and writes
# results/<bucket>/<run>/<run>.txt, <run>-stats.csv, <run>-k6.csv.gz
benchmarks/run.sh reject-path/run-b  -e MODE=breakpoint -e PEAK=1500 -e COURSE_ID=FLASH-100
benchmarks/run.sh reject-path/run-c1 -e MODE=constant   -e RATE=1100 -e COURSE_ID=FLASH-100
benchmarks/run.sh reject-path/run-c2 -e MODE=constant   -e RATE=1250 -e COURSE_ID=FLASH-100
benchmarks/run.sh reject-path/run-c3 -e MODE=constant   -e RATE=1400 -e COURSE_ID=FLASH-100

# Write path: create the 100K-seat course once, then run with lag tracking and a persistence check
curl -X POST localhost:8080/api/v1/courses -H 'Content-Type: application/json' -H 'X-User-Role: ADMIN' \
    -d '{"courseId":"FLASH-BIG","courseName":"Flash Sale Write-Path Benchmark","department":"Benchmark","totalCapacity":100000}'
benchmarks/write-path.sh write-path/run-w -e MODE=constant -e RATE=1250 -e COURSE_ID=FLASH-BIG

# Overload spike (Run A was run directly, before run.sh existed)
k6 run -e COURSE_ID=FLASH-100 --summary-trend-stats="avg,p(50),p(95),p(99),max" \
    tests/load/flash-sale-10k.js | tee benchmarks/results/overload/run-a/run-a.txt
```

- [tests/load/flash-sale-10k.js](../tests/load/flash-sale-10k.js): k6 script, `MODE=spike|breakpoint|constant`; rates are **iterations/s** (×2 for requests/s)
- [reset-course.sh](reset-course.sh): waits for the worker to drain, deletes the course's enrollments, restores seats in Postgres and Redis, clears `idempotency:*` / `rate_limit:*` keys
- [run.sh](run.sh): reset + `docker stats` sampler + k6
- [write-path.sh](write-path.sh): run.sh + consumer-lag sampler; after k6 ends, waits for lag 0 and checks accepted (Redis) == persisted (Postgres) with no new DLQ entries

Results layout: `results/reject-path/`, `results/write-path/`, `results/overload/`, and `results/discarded/` (runs excluded from the report), one folder per run.

## Method

1. **Run B, breakpoint:** linear ramp 0 → 1,500 iterations/s over 5 min to locate the knee. Latency rises sharply between 1,250 and 1,300 iterations/s.
2. **Runs C, confirmation:** 60 s at a constant rate below, at, and above the knee. The reported throughput is the highest rate held with stable latency and ~0 dropped iterations.
3. **Run A, overload:** the original spike to 10,000 iterations/s (20,000 requests/s offered) to see behaviour far past capacity.
4. **Run W, write path:** the C2 rate (1,250 iterations/s for 60 s) against a 100,000-seat course, so every first click is accepted and published to Kafka. Consumer lag is sampled throughout; after k6 ends, the script waits for lag 0 and compares accepted vs persisted.

## Results

### Reject path (Runs A–C)

| Run | Profile | Offered (req/s) | Achieved (req/s) | p50 | p95 | p99 | 5xx / network errors | Dropped iterations | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| C1 | constant | 2,200 | 2,186 | 9.9 ms | 114 ms | 251 ms | 0 | 265 (0.4%, all in first 10 s) | stable |
| **C2** | constant | 2,500 | **2,495** | 13.2 ms | **79 ms** | 177 ms | 0 | 83 (0.1%) | **stable** |
| **C2 repeat** | constant | 2,500 | **2,498** | 9.0 ms | **59 ms** | 128 ms | 0 | 0 | **stable** |
| C3 | constant | 2,800 | 2,720 | 40.6 ms | 444 ms | 535 ms | 0 | 1,764 (2.1%) | degrading: p50 climbs 10 → 378 ms within the minute |
| B | breakpoint | 0 → 3,000 | 1,488 avg | 13.0 ms | 337 ms | 443 ms | 0 | 1,329 | knee at ~2,500–2,600 req/s |
| A | spike | up to 20,000 | 752 | 3.6 s | 13.3 s | 13.8 s | 4 (0.01%) | 205,671 | saturated |

Latency figures are over the whole run. Per-10 s windows for the C2 repeat stayed at p95 18–30 ms for 50 s, with one window at 135 ms (likely a GC or pool pause).

**Resource use at 2,500 req/s** (C2 repeat, average CPU, 100% = one core):

| gateway | enrollment-service | kafka | redis | postgres, worker |
|---|---|---|---|---|
| 497% | 408% | 67% | 54% | ~1–2% |

The two JVM services use ~9 cores between them; with k6 on the same 8-core / 16-thread host, the machine is CPU-bound. Redis, Kafka and Postgres have ample headroom.

### Write path (Run W)

| Offered (req/s) | Achieved (req/s) | p50 | p95 | p99 | 5xx / network errors | Dropped | Accepted (Redis) | Persisted (Postgres) | DLQ delta |
|---|---|---|---|---|---|---|---|---|---|
| 2,500 | 2,497 (1,249 × 202 + 1,249 × 409 per s) | 25.5 ms | 102 ms | 161 ms | 0 | 0 | 75,000 | **75,000** | **0** |

- **Consumer lag** stayed between 137 and 316 events during the run (~0.1–0.25 s of traffic) and reached **0 within 2 s** of k6 ending. The worker kept up with 1,250 writes/s on a single partition.
- **Latency cost of accepting:** p95 rises from 59–79 ms (rejections) to 102 ms, from the extra Redis writes and the Kafka publish.
- **CPU (average):** enrollment-service 414%, gateway 387%, **Kafka 204%**, Postgres 59%, worker 49%, Redis 44%. Kafka becomes the third-largest consumer; Postgres and the worker remain well below saturation.

## Correctness

Measured in every run, including the overload:

- **No overselling:** exactly **100 × 202** for 100 seats in every run; Postgres held exactly 100 enrollments after Run A drained.
- **Idempotency:** the `double-click rejected (409)` check passed **100%** in runs B and C (65,000–82,000 duplicates per C run). In Run A, 4 of 16,828 duplicates failed; these were the four 500s below, not wrong answers.
- **Durability (write path):** every accepted enrollment was persisted: 75,000 in Redis, 75,000 in Postgres, 0 sent to the DLQ.
- **Errors under capacity:** 0 across all B, C and W runs.
- **Run A's 4 × 500:** produced by the gateway when enrollment-service reset its connections (`Connection reset by peer`) at peak load. The requests never reached the controller (no service-side error logs), so this is connection-level overload, not an application error.

## Caveats

- **Single laptop, k6 on the same machine.** k6 competes with the services for CPU, so the numbers understate what the services could do on dedicated hardware.
- **Docker Desktop on macOS.** Containers run in a Linux VM, and traffic to `localhost:8080` goes through Docker Desktop's port forwarding, a known throughput limiter.
- **One instance of everything,** including a single Kafka broker. No replication, no horizontal scaling, untuned defaults.
- **Traffic is almost all rejections.** With 100 seats, 99.98% of requests are 400 (full) or 409 (duplicate); only 100 per run take the full path through Kafka. Realistic for a flash sale, but this measures the rejection path. **Run A in particular** (17K iterations against 100 seats) is a rejection-throughput test far past capacity, not a throughput figure.
- **Short runs, few repeats:** 60 s per confirmation run; the 2,500 req/s rate was run twice, the others once. A third C2 run was discarded because the host was busy with other work during it.
- **Worker maximum throughput not measured.** The worker never fell behind in Run W, so the 2 s drain shows it keeps up at 1,250 writes/s, not its ceiling. Measuring that needs a backlog (e.g. pause the worker during a run, then time the drain).
- **One partition per course.** A single hot course's writes are serialized through one partition and consumer by design; more partitions or worker instances do not speed up one course.
- **Lag sampled every ~5 s**, not the intended 2 s: each `kafka-consumer-groups` call takes ~2–3 s.
- **Run A predates two script changes:** its `http_req_failed` used k6's default (2xx/3xx only), and it ran without the reset script.

## What would raise the limit

Ordered by the evidence above:

1. **Short-circuit when sold out.** Every rejection still costs 3 Redis round-trips (rate limit, `SETNX`, seat Lua) and 2 HTTP hops. A locally cached "sold out" flag in the gateway or service would answer the bulk of flash-sale traffic almost for free.
2. **Make the gateway cheaper.** It is the top CPU consumer, above the service doing the actual work. Profile it, tune JVM heap and the Netty client pool, or run more gateway instances.
3. **Drop per-request INFO logging.** enrollment-service logs every duplicate at INFO (~1,250 lines/s at the measured rate).
4. **Test a larger Redis pool.** `lettuce.pool.max-active: 8` is shared by thousands of virtual threads; unverified as a bottleneck.
5. **Scale out** gateway and enrollment-service behind a load balancer. Only pays off with more cores; this host is already saturated.

For more accurate numbers rather than a faster system: run k6 on a separate machine and the stack on native Linux.
