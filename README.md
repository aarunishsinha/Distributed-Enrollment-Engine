# Distributed High-Concurrency Enrollment Engine

A fault-tolerant, high-concurrency event-driven course registration API built natively to handle massive "flash sale" traffic spikes.

Tested up to **2,000 requests/second** with absolute zero inventory overbooking and bulletproof idempotency mechanisms to catch and deny hostile "panic double-clicks".

## 🏗 System Architecture 
- **Language**: TypeScript (Node.js)
- **API Cache & Concurrency Controller**: Redis 7
- **Event Bus / Message Queue**: Apache Kafka (KRaft mode)
- **Persistent Storage**: PostgreSQL 15

### Core Components
1. **API Gateway (Producer)**: Exposes `POST /v1/enrollments`. 
   - Intercepts incoming requests and uses an immediate **Redis Idempotency Check** to prevent duplicate submissions from the same user session.
   - For valid, non-duplicate requests, it utilizes an atomic **Redis Lua Script** to instantly check real-time course capacity and reserves the seat natively in memory.
   - Successful seat reservations are then published asynchronously to the Kafka `enrollment_reserved` topic.

2. **Background Worker (Consumer)**: Listens to Kafka.
   - Drains the `enrollment_reserved` event stream at a controlled, steady pace. Continually commits Kafka offsets to ensure the system is crash-resistant.
   - Executes a strict idempotent PostgreSQL `UPSERT` utilizing a `UNIQUE(course_id, user_id)` constraint. 
   - Non-transient errors (like foreign key violations or data mismatch errors) are gracefully dead-lettered to an `enrollment_dlq` topic.

## 🚀 Quick Start (Local Setup)
Ensure you have **Node.js 20+** and **Docker Desktop** installed.

#### 1. Start the Infrastructure (Database, Redis, Kafka)
```bash
docker-compose up -d
```
*Wait a few seconds for the `kafka-setup` container to finish creating the topics.*

#### 2. Install Dependencies
```bash
npm install
```

#### 3. Build & Initialize the Data
This initializes the PostgreSQL container schema and pre-loads the available seats to Redis.
```bash
npm run build
npm run init
```

#### 4. Start the Application
Run these in two separate terminal windows:
```bash
npm run start:api
```
```bash
npm run start:worker
```

## 💥 Chaos Engineering: Flash Sale Load Test
I have included `flash-sale-test.js` in this repository, a hostile `k6` benchmark script I wrote to mathematically prove the system's lock mechanisms.

I simulated 28,000 inbound requests within 10 seconds (Peak: **2,000 req/sec**). Additionally, the script forced every single simulated user to "Panic Double Click" exactly 50ms apart. Course `CS400` had exactly **50** available seats. 

```bash
# Ensure the infrastructure is running with `npm run start:api` and `npm run start:worker`
brew install k6
k6 run flash-sale-test.js
```

### The Test Results
1. **Idempotency Held**: 13,949 duplicate "Panic Clicks" were immediately blocked at the Redis edge and returned a safe HTTP 409 Conflict.
2. **Concurrency Safe**: 13,949 users were gracefully denied with HTTP 400 "Course Full" once the 50 seats were gone.
3. **Zero Overbooking Guarantee**: A direct query to the PostgreSQL production tables confirms exactly 50 unique rows were created.

#### Post-Test Verification Queries:
```sql
docker exec -i enrollment_postgres psql -U postgres -d enrollment_db -c "SELECT COUNT(*) FROM enrollments WHERE course_id='CS400';"

 count 
-------
    50
(1 row)
```

**Did anyone accidentally double book?**
```sql
docker exec -i enrollment_postgres psql -U postgres -d enrollment_db -c "SELECT user_id, COUNT(*) FROM enrollments WHERE course_id = 'CS400' GROUP BY user_id HAVING COUNT(*) > 1;"

 user_id | count 
---------+-------
(0 rows)
```
