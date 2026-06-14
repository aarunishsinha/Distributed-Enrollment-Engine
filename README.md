# University ERP — Distributed Enrollment Engine

A production-grade, microservices-based university enrollment system designed to handle **flash-sale traffic patterns** at **10,000+ RPS** with zero overbooking.

## Architecture

```
Frontend (React)  →  API Gateway (Spring Cloud Gateway)
                         │  Token Bucket Rate Limiter
                         ├─→ Enrollment Service (Spring Boot + Virtual Threads)
                         │       Redis Lua: Seat Reservation + Idempotency
                         │       Kafka Produce → enrollment_reserved
                         │
                         └─→ Course Service (Spring Boot + JPA)
                                 CRUD, Search, Analytics, User Management
                                        
Kafka (6 partitions) → Enrollment Worker (Batch Consumer)
                            Batch UPSERT to PostgreSQL (200 rows/batch)
                            DLQ routing on failure → enrollment_dlq
```

| Component | Technology | Purpose |
|-----------|-----------|---------|
| API Gateway | Spring Cloud Gateway | Rate limiting (Token Bucket), routing, CORS |
| Enrollment Service | Spring Boot 3.3 + Java 21 Virtual Threads | Hot path: idempotency → seat reservation → Kafka produce |
| Enrollment Worker | Spring Boot 3.3 + Spring Kafka | Batched Kafka consumer → PostgreSQL UPSERT |
| Course Service | Spring Boot 3.3 + Spring Data JPA | CRUD, substring search, analytics, user management |
| Frontend | React 18 + Vite | Course catalog, enrollment, admin dashboard |
| Database | PostgreSQL 15 | Persistent storage for users, courses, enrollments |
| Cache | Redis 7 | Seat cache, idempotency, rate limiting |
| Message Broker | Apache Kafka (KRaft) | Async write decoupling, DLQ |

## Quick Start

### Prerequisites
- **Java 21** (for local development)
- **Docker Desktop** (for infrastructure + full-stack)
- **Node.js 20+** (for frontend development)
- **Maven 3.9+** (for building Java services)

### Option 1: Full Stack via Docker Compose
```bash
cd docker
docker compose up --build -d
```
Wait ~2 minutes for all services to start. Then:
- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Enrollment Service**: http://localhost:8081
- **Course Service**: http://localhost:8082

### Option 2: Local Development

1. **Start infrastructure only:**
```bash
cd docker
docker compose up postgres redis kafka kafka-setup -d
```

2. **Build and run each service** (in separate terminals):
```bash
# Enrollment Service
cd enrollment-service && mvn spring-boot:run

# Enrollment Worker
cd enrollment-worker && mvn spring-boot:run

# Course Service
cd course-service && mvn spring-boot:run

# API Gateway
cd api-gateway && mvn spring-boot:run
```

3. **Start frontend:**
```bash
cd frontend && npm install && npm run dev
```

## API Endpoints

### Enrollment (via Gateway at :8080)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/enrollments` | Enroll in a course (rate limited) |

### Courses
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/courses` | List courses (paginated) |
| GET | `/api/v1/courses/search?q=comp` | Substring search |
| GET | `/api/v1/courses/{id}` | Course detail |
| POST | `/api/v1/courses` | Add course (admin) |
| DELETE | `/api/v1/courses/{id}` | Remove course (admin) |

### Admin
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/admin/analytics` | Enrollment analytics |
| GET/POST/DELETE | `/api/v1/admin/users` | User management |

## Design Document

See [DESIGN.md](DESIGN.md) for:
- Requirements & NFRs
- Entity relationship diagrams
- Full throughput math (10K RPS analysis)
- Rate limiting algorithm comparison
- DLQ strategy with partial batch failure handling
- Inter-service communication decisions

## Project Structure

```
├── old/                    # Original TypeScript prototype
├── docker/                 # Docker Compose + PostgreSQL schema
├── api-gateway/            # Spring Cloud Gateway (rate limiter)
├── enrollment-service/     # Flash-sale hot path
├── enrollment-worker/      # Kafka batch consumer
├── course-service/         # CRUD, search, analytics, users
├── frontend/               # React + Vite
├── data/                   # Seed datasets
├── DESIGN.md               # System design document
└── README.md               # This file
```
