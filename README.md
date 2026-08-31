# Spring Order

> An order management + promotion-rules platform, gradually evolving into a **high-concurrency distributed order platform**.
> Backend: Spring Boot + Spring Data JPA (Flyway migrations, `@Version` optimistic lock on inventory; embedded H2 by default, switchable to Postgres). Frontend: React/Vite.
>
> Chinese documentation: [README.zh.md](./README.zh.md) · Architecture & data flow: [ARCHITECTURE.md](./ARCHITECTURE.md).

---

## Table of Contents

- [1. Overview](#1-overview)
- [2. Tech Stack](#2-tech-stack)
- [3. Project Structure](#3-project-structure)
- [4. Quick Start](#4-quick-start)
- [5. API Reference](#5-api-reference)
- [6. Promotion Rules](#6-promotion-rules)
- [7. Local Environment Notes](#7-local-environment-notes)
- [8. Frontend Mock (MSW)](#8-frontend-mock-msw)
- [9. Testing & Coverage](#9-testing--coverage)
- [10. Architecture Evolution (P0–P4)](#10-architecture-evolution-p0p4)
- [11. Security Hardening](#11-security-hardening)

---

## 1. Overview

`spring-order` demonstrates typical backend business scenarios and **progressively evolves toward a distributed, high-concurrency architecture**: order placement, promotion-rule calculation, user listing, order queries, and inventory reservation.

**P0** established the persistence foundation: data is stored via Spring Data JPA (embedded H2 file DB by default — zero external dependencies, survives restarts; switchable to real Postgres via the `postgres` profile), Flyway manages the schema, and inventory deduction uses the `inventory` table + `@Version` optimistic lock to eliminate overselling at the database level.

Key capabilities:

- Order flow + business rules (promotion / inventory validation)
- Unified front/back response contract (`{ order, promotion }` envelope)
- Exception handling with correct HTTP semantics (400 / 404 / 409 / 429 / 503)
- Unit + integration tests with a JaCoCo coverage gate (80%)

> Package namespace: `com.example.order` (Java example placeholder, left unchanged).

---

## 2. Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 3.2.5 · Java 17 · Maven · Lombok |
| Frontend | React 18 · TypeScript · Vite 5 · React Router 6 |
| Dev-time Mock | MSW 2.4 (Mock Service Worker) |
| Testing | JUnit 5 · Mockito · `@WebMvcTest` / `@SpringBootTest` · EmbeddedKafka (`spring-kafka-test`) · JaCoCo (80% gate) |
| Storage | Spring Data JPA + Flyway + H2 (file-based, zero-dep) / PostgreSQL (`postgres` profile) · `@Version` optimistic lock on inventory |
| Cache / Distributed Lock | Redis (`spring-boot-starter-data-redis`) + `RedisLockService` (SET NX PX + Lua unlock) + `RedisCacheManager`, enabled only under the `redis` profile |
| Messaging / Events | Kafka (`spring-kafka`) + Transactional Outbox (`OutboxService` / `OutboxRelay`), enabled only under the `kafka` profile |
| Reliability | Resilience4j (rate limit / circuit breaker / retry) + `Idempotency-Key` idempotency table (JPA, zero external dependency) |
| Observability | Actuator + Micrometer (`micrometer-registry-prometheus`), exposes `/actuator/prometheus` |
| Gateway / Orchestration | nginx load balancing + Docker Compose multi-instance full stack (`backend/Dockerfile` multi-stage image) |
| Migrations | Flyway: `V1__init.sql` (orders / inventory), `V2__idempotency.sql` (idempotency keys), `V3__outbox.sql` (outbox), H2/PostgreSQL compatible |

---

## 3. Project Structure

```
spring-order/
├── Makefile                 # Wrapped commands (install / run / test / ci ...)
├── docker-compose.yml       # Full stack: order-svc replicas + redis + postgres + kafka + nginx + prometheus + grafana
├── backend/                 # Spring Boot backend
│   ├── pom.xml
│   ├── Dockerfile           # Multi-stage: maven build -> eclipse-temurin JRE
│   └── src/main/java/com/example/order/
│       ├── controller/      # OrderController (with Idempotency-Key header), UserController, EventController
│       ├── service/         # OrderService, PromotionService, InventoryService
│       │                   # RedisLockService, IdempotencyService, OutboxService
│       │                   # OutboxRelay, OrderEventLogger (sample Kafka consumer)
│       ├── dto/             # Request/response models (incl. promotion envelope)
│       ├── model/           # Order, Inventory, User, CartItem, IdempotencyRecord, OutboxEvent
│       ├── exception/       # Global exception handling + custom exceptions (optimistic lock 409 / idempotency conflict 409 / rate limit 429 / circuit open 503)
│       └── resources/
│           ├── application.yml            # Default: H2 file DB + Flyway + Actuator/Prometheus
│           ├── application-postgres.yml   # postgres profile
│           ├── application-redis.yml      # redis profile (distributed lock + cache)
│           ├── application-kafka.yml      # kafka profile (outbox relay + topic)
│           ├── application-dev.yml        # dev profile (enables H2 console locally)
│           ├── application-prod.yml       # prod profile (narrows Actuator, separate mgmt port)
│           └── db/migration/
│               ├── V1__init.sql           # orders / inventory tables + seed inventory
│               ├── V2__idempotency.sql    # idempotency keys table
│               └── V3__outbox.sql         # outbox event table
├── conf/nginx.conf          # Gateway: replica round-robin + passive health check, exposed on :80
├── prometheus/prometheus.yml# Scrapes both replicas' /actuator/prometheus
├── grafana/provisioning/... # Prometheus datasource (admin/admin)
├── scripts/                 # load-test.js (k6) + load-test.py (locust)
└── frontend/                # React/Vite frontend (containerized, see P4)
    ├── Dockerfile           # Single stage: nginx serves pre-built dist, proxies /api /actuator -> gateway
    ├── nginx.conf           # In-container nginx: SPA history fallback + backend reverse proxy
    ├── .dockerignore        # Excludes node_modules/src, COPYs only dist
    ├── src/
    │   ├── api/client.ts    # fetch wrapper (getOrders / getEvents / getInstanceInfo / getPrometheus)
    │   ├── mocks/           # MSW handlers / server / browser (DEV only)
    │   ├── components/      # OrderForm, UserList
    │   ├── pages/           # OrderPage (order form + order list), UsersPage, StatusPage (runtime)
    │   └── types/index.ts   # Frontend types (aligned with backend contract)
    ├── vite.config.ts       # Port 3000 + /api + /actuator proxy to 8080
    └── package.json
```

---

## 4. Quick Start

### Prerequisites

- Java 17+, Maven 3.8+
- Node 24+
- Docker / OrbStack (only for the full-stack / Postgres / Redis / Kafka paths)

### Run the backend

```bash
cd backend
mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

Listens on **http://localhost:8080**.

> ⚠️ On throttled mirrors use `-o` (offline) and pin the port explicitly — see [§7 Local Environment Notes](#7-local-environment-notes).

### Run the frontend

```bash
cd frontend
npm install
npm run dev          # Vite, default port 3000, /api proxied to 8080
```

Open **http://localhost:3000**.

> By default the frontend talks to the real backend through the Vite proxy (`/api` → `:8080`). To run the UI **without a backend**, use `VITE_ENABLE_MOCK=true` (see [§8](#8-frontend-mock-msw)).

### One command (Makefile)

```bash
make install         # backend mvn clean install + frontend npm install
make run             # backend only (= backend-run; see notes)
make test            # backend tests
make ci              # backend tests + JaCoCo coverage gate (simulates CI)
```

### Full stack via Docker Compose

```bash
docker compose up -d --build
# gateway :80 / frontend UI :18083 / prometheus :9090 / grafana :3000
# scale: docker compose up -d --scale order-svc-1=2 --scale order-svc-2=2 --build
```

Credentials use `${VAR:-default}` interpolation; real values come from a git-ignored root `.env` (template: `.env.example`). Zero-config `docker compose up` still works with the defaults.

---

## 5. API Reference

Base URL: `http://localhost:8080`

### 5.1 User list

```
GET /api/users
```

Returns 5 preset users (`@example.com`).

```json
[
  { "id": "u1", "name": "Alice Johnson", "email": "alice@example.com" },
  { "id": "u2", "name": "Bob Smith", "email": "bob@example.com" }
]
```

### 5.2 Create order

```
POST /api/orders
```

Request body (`OrderCreateRequest`):

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `userId` | string | ✅ | Non-blank (`@NotBlank`) |
| `amount` | number | ✅ | Positive (`@Positive`) |
| `stockId` | string | ❌ | Inventory pool id, defaults to `"DEFAULT"` (server-managed via `inventory` table) |
| `requiredStock` | int | ❌ | Units to deduct, defaults to `1` |

**New-user detection**: if `userId` has no prior order in the `orders` table, the order is treated as the user's first order.
**Inventory check**: performed by `InventoryService` on the `inventory` table + `@Version` optimistic lock; insufficient stock returns `400 Insufficient Stock`. The legacy client-side `availableStock` field was removed — available stock is now a server-managed state.
**Idempotency header (optional)**: `POST /api/orders` also accepts an `Idempotency-Key` header (unique string). A repeat with the same key hitting a completed record replays the first response (`200`); concurrent submissions return `409`; a business failure releases the key so it can be safely retried. See [§10 P2](#p2--reliability-idempotency--resilience4j).

Success returns **201 Created** with a `Location` header and a `{ order, promotion }` envelope:

```json
{
  "order": {
    "id": "b1f0c2...",
    "userId": "alice",
    "amount": 50,
    "discount": 10,
    "finalAmount": 40,
    "status": "CREATED",
    "createdAt": "2026-07-16T21:00:00"
  },
  "promotion": {
    "originalAmount": 50,
    "discount": 10,
    "finalPrice": 40,
    "appliedRule": "new_user"
  }
}
```

`appliedRule` ∈ `new_user` | `full_reduction` | `none`.

### 5.3 Query orders

```
GET /api/orders/{id}    # Single order; 404 if not found
GET /api/orders         # Order list (served from Redis cache `orders-all` under the redis profile)
```

### 5.4 Error codes

| HTTP | Scenario | Notes |
|------|----------|-------|
| 400 | Validation failure / insufficient stock | Constraint violation or `InsufficientStockException` |
| 404 | Order not found | `OrderNotFoundException` (message omits the id to avoid info leakage) |
| 409 | Optimistic-lock conflict / idempotency in-progress | `OptimisticLockingFailureException` (retry still conflicts) → 409; `IdempotencyConflictException` (concurrent same-key submit) → 409 |
| 429 | Rate limit triggered | Resilience4j `@RateLimiter` (`orderCreate` instance, default 10/s) → `RequestNotPermitted` → 429 |
| 503 | Circuit breaker open | Resilience4j `@CircuitBreaker` open → `CallNotPermittedException` → 503 |

### 5.5 Order event stream (P3 Outbox)

```
GET /api/events        # Most recent PUBLISHED outbox events, up to 20
```

Returns events relayed by the Outbox to the Kafka `order-events` topic; the frontend "runtime" page renders this as an event stream:

```json
[
  {
    "id": "9f2c...",
    "eventType": "OrderCreated",
    "aggregateId": "b1f0c2...",
    "status": "PUBLISHED",
    "createdAt": "2026-07-17T09:00:00",
    "publishedAt": "2026-07-17T09:00:01"
  }
]
```

### 5.6 Runtime info (P4)

```
GET /actuator/info        # Includes instanceId (hostname of the responding replica), demonstrates gateway round-robin
GET /actuator/prometheus  # Raw metrics text; frontend parses HTTP request count / latency / circuit calls
```

---

## 6. Promotion Rules

Implemented in `PromotionService.applyPromotion(amount, isNewUser, requiredStock, availableStock)`:

| Rule | Condition | Discount |
|------|-----------|----------|
| Rule 1 — New-user first order | `isNewUser == true` | `-10` |
| Rule 2 — Threshold reduction | `amount >= 100` | `-20` |
| Rule 3 — Not stackable (threshold priority) | Both Rule 1 & 2 apply | Only Rule 2 (threshold takes priority) |
| Rule 4 — Stock check | `requiredStock > availableStock` | Throws `InsufficientStockException` → 400 (redundant guard; real enforcement is in `InventoryService`) |

Evaluation order (given `amount > 0`):

1. **Check stock first** (Rule 4, redundant guard) — the real interception is in `InventoryService` (`inventory` table + `@Version`).
2. If `amount >= 100` → threshold reduction `-20` (Rule 2, priority).
3. Else if new user → `-10` (Rule 1).
4. Else no discount.

> `amount <= 0` is treated as invalid input with no discount; if the new-user discount would push the amount negative it is floored to `0`.

---

## 7. Local Environment Notes

> The two points below are **environment-specific pitfalls on this machine**, not code defects. On a host that can reach Maven Central directly, `mvn spring-boot:run` works as-is.

### 7.1 Maven needs offline mode `-o`

This machine's `~/.m2/settings.xml` points at an Aliyun mirror that is heavily throttled in the sandbox (some artifacts at only a few hundred B/s), so online `mvn spring-boot:run` stalls on metadata downloads. All needed dependencies are already cached locally, so offline mode starts instantly:

```bash
mvn -o spring-boot:run
```

> The Makefile's `backend-run` is plain `mvn spring-boot:run` (no `-o`) and will stall here; use the offline command above, or add `-o` to the Makefile.

### 7.2 Pin the port to 8080

`application.yml` declares `server.port: 8080`, but `mvn spring-boot:run` once bound a **random port** (e.g. 61705) on this machine for reasons not fully isolated. CLI arguments win, so pin it explicitly:

```bash
mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

### 7.3 Frontend port conflict

`frontend/vite.config.ts` defaults to `port: 3000` and proxies `/api -> 8080`. If 3000 is taken, start on another port:

```bash
cd frontend && npx vite --port 3001
```

---

## 8. Frontend Mock (MSW)

MSW is **off by default** — the frontend talks to the real backend through the Vite proxy (`/api` → `:8080`), so orders are actually persisted and idempotency/Outbox work for real. To run the UI **without a backend**, start the frontend with `VITE_ENABLE_MOCK=true`:

```bash
cd frontend && VITE_ENABLE_MOCK=true npx vite
```

`src/main.tsx` then starts the MSW Worker (under `import.meta.env.DEV && VITE_ENABLE_MOCK === 'true'`), intercepting `/api/*` with mock data. The mocked promotion semantics match the backend (≥100 → −20 priority; new user → −10). `onUnhandledRequest: 'bypass'` is configured, so requests not covered by the mock still go to the network.

---

## 9. Testing & Coverage

```bash
make backend-test            # Run backend tests (controller / service / promotion / integration)
make backend-coverage        # Generate JaCoCo report (target/site/jacoco/index.html)
make backend-coverage-check  # Verify the 80% coverage gate
make ci                      # = test + coverage-check (simulates the CI pipeline)
```

Coverage includes: successful order (201), new-user derivation, threshold-priority, insufficient stock (400), order-not-found (404), response-contract alignment, idempotency branches, rate-limit (429), outbox relay, etc.

> Running the backend generates an H2 file DB under `backend/data/` (git-ignored). You can open **http://localhost:8080/h2-console** in dev profile to inspect the `orders` / `inventory` tables.

---

## 10. Architecture Evolution (distributed direction)

`spring-order` is evolving from a single-instance application into a **high-concurrency distributed order platform**. Each pain point maps to an evolution stage:

| Current shape | Distributed target | Stage |
|---------------|--------------------|-------|
| `ConcurrentHashMap` in-memory, lost on restart | Shared persistence (Postgres) + stateless multi-instance | ✅ **P0** |
| Read-then-check stock (oversell risk) | DB optimistic lock (`@Version`) + retry, no oversell | ✅ **P0** |
| Single instance, concurrency = one box | Multi-instance + Redis distributed lock/cache | ✅ **P1** |
| No idempotency, gateway retry duplicates orders | `Idempotency-Key` + Resilience4j rate/circuit/retry | ✅ **P2** |
| Synchronous order coupling | Order events on Kafka (Outbox) decoupling inventory/payment/notify | ✅ **P3** |
| Single entry + no observability | nginx gateway LB + Actuator/Prometheus/Grafana | ✅ **P4** |

### P0 · Persistence foundation

- **Storage**: Spring Data JPA; embedded **H2 file DB** by default (zero-dep, survives restart), switchable to real Postgres via the `postgres` profile.
- **Migrations**: Flyway (`V1__init.sql`, H2/Postgres compatible), `ddl-auto: none`.
- **No oversell**: inventory in the `inventory` table; `InventoryService.reserve()` uses `@Transactional` + `@Version`; conflict throws `OptimisticLockingFailureException`, and `OrderService` retries (up to 5 times) instead of overselling. Mapped to **409 Conflict**.
- **Switch to Postgres** (needs local Docker):
  ```bash
  docker compose up -d db
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=postgres"
  ```

### P1 · Redis distributed lock + cache

Moves from single-instance to stateless horizontal scaling:

- **Distributed lock**: `RedisLockService` (SET `lock:{stockId}` NX PX + Lua atomic unlock). When Redis is available, `OrderService.reserveWithRetry` serializes same-`stockId` deductions across instances, eliminating optimistic-lock retry storms; on lock miss it degrades to direct execution (DB optimistic lock still guarantees no oversell). Lua unlock only deletes the key when the token still matches, avoiding deleting others' locks.
- **Cache**: `RedisConfig` (`@ConditionalOnProperty(spring.data.redis.host)` + `@EnableCaching`) configures `RedisCacheManager` (JSON serialization, 5 min TTL). `getOrder` / `getAllOrders` are `@Cacheable`; `createOrder` is `@CacheEvict(orders-all)`.
- **Graceful degradation (key)**: Redis is **optional infrastructure**. It is enabled only under the `redis` profile; by default `RedisLockService` and `@EnableCaching` are not even loaded, and `OrderService` falls back to pure DB optimistic lock with no cache — **runs and tests with zero dependencies**.
- **Enable** (needs local Docker):
  ```bash
  docker compose up -d
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=redis,postgres"
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --spring.profiles.active=redis,postgres"
  ```

### P2 · Reliability: idempotency + Resilience4j

Keeps the order API safe under network retries / gateway replays / traffic spikes:

- **Idempotency (`Idempotency-Key`)**: `POST /api/orders` accepts an optional `Idempotency-Key` header. The server records each key in the `idempotency_keys` table (JPA-persisted, zero-dep):
  - A **second request with the same key hitting a completed record** → replays the first `OrderWithPromotion` (**200 OK**), no duplicate order, no duplicate stock deduction.
  - A **concurrent request with the same key still IN_PROGRESS** → returns **409 Conflict**, blocking double submission.
  - A unique constraint + concurrent-insert conflict guarantees only one request processes a given key across instances.
  - On business failure (e.g. 400 insufficient stock) the key is **released**, so the client can safely retry with the same key (no side effect consumed).
- **Rate limit / circuit breaker / retry (Resilience4j)**: instance `orderCreate` on `OrderService.createOrder`:
  - `@RateLimiter` (default 10/s) → 429 on exceed.
  - `@CircuitBreaker` (COUNT_BASED, 50% failure opens for 5s) → 503 when open; expected business exceptions (e.g. `InsufficientStockException`) are not counted.
  - `@Retry` (up to 3) for transient faults (`OptimisticLockingFailureException` / `DataAccessException`), skipping idempotency conflicts and insufficient stock.
  - Woven via Spring AOP; `RequestNotPermitted` / `CallNotPermittedException` mapped to 429 / 503 by `GlobalExceptionHandler`.

### P3 · Event-driven: Kafka + Transactional Outbox

Decouples order placement from downstream (inventory/payment/notify/audit) and guarantees atomic "persist + publish":

- **Transactional Outbox**: `OrderService.createOrder` writes an `OrderCreated` event to `outbox_events` in the **same DB transaction** as the `orders` row. State update and event enqueue can never diverge — both succeed or both roll back.
- **Relay (OutboxRelay)**: a `@ConditionalOnProperty(spring.kafka.bootstrap-servers)` scheduled task (`fixedDelay` 1s) scans `PENDING` rows, sends each via `kafkaTemplate.send` to `order-events`, then `markPublished` (REQUIRES_NEW). **at-least-once**: if the relay crashes between send-success and mark-published, the event is resent next poll — so **downstream must be idempotent** (ties back to P2's `Idempotency-Key`). Failed sends stay `PENDING` and retry; events are never lost, even if Kafka is briefly down.
- **Graceful degradation (key)**: Kafka is **optional**. Enabled only under the `kafka` profile; without a broker these beans are never created and `outbox_events` rows simply stay `PENDING` (replayed once a broker exists) — **runs and tests with zero dependencies**.
- **Sample consumer**: `OrderEventLogger` (`@KafkaListener` printing `order-events`) is a sample sink. Real scenarios drive fulfillment, notifications, read-model updates — all keyed by `orderId`.
- **Enable** (needs local Docker):
  ```bash
  docker compose up -d
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=redis,postgres,kafka"
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --spring.profiles.active=redis,postgres,kafka"
  ```

### P4 · Multi-instance deployment: gateway + monitoring

Wires all P0–P3 capabilities under truly multiple instances, demonstrating horizontal scaling and observability:

- **Multi-instance + gateway**: `docker-compose.yml` brings up **2 order-svc replicas** (`order-svc-1` / `order-svc-2`) sharing the same Redis + Postgres + Kafka, fronted by an **nginx gateway** (`conf/nginx.conf`) doing round-robin LB on a single `:80` entry. `backend/Dockerfile` builds the multi-stage image; `docker compose up -d --build` starts the full stack.
- **Frontend UI container**: `frontend/` is containerized — `frontend/Dockerfile` serves pre-built `dist` via nginx and reverse-proxies `/api` and `/actuator` to the gateway (same-origin, no CORS). A `frontend` service (default `:18083`, avoiding local 80/3000 clashes) exposes the full UI covering P1–P4: order page (form + list, list via Redis `@Cacheable`), users page, and **runtime page (`/status`)** — showing the responding replica (`/actuator/info` `instanceId`, alternating across replicas on refresh), HTTP metrics (parsed from `/actuator/prometheus`), and the recent Kafka order event stream (`GET /api/events`).
- **Observability**: Actuator + Micrometer expose `/actuator/prometheus`; `prometheus/prometheus.yml` scrapes both replicas, `grafana/provisioning` injects the Prometheus datasource (admin/admin). Targets show both replicas scraped separately; Grafana splits by instance label, visualizing traffic spread across replicas.
- **Health-check pitfall (fixed)**: `spring-boot-starter-data-redis` on the classpath auto-registers `RedisReactiveHealthIndicator` **regardless of** `RedisConfig`'s `@ConditionalOnProperty` — without Redis it pings `localhost:6379` and fails, falsely reporting `/actuator/health` as `DOWN`. Fixed by disabling `management.health.redis.enabled` by default, enabling it only under the `redis` profile.
- **Degradation unchanged**: P4 only adds orchestration + monitoring, not runtime-contract changes. Replicas connect to middleware only under `redis,postgres,kafka`; nginx passive health check (3 fails → evict 30s) keeps traffic flowing if a replica dies; Actuator exposes only `health`/`info`/`prometheus` (see [§11](#11-security-hardening)).

---

## 11. Security Hardening

This project defaults to "runnable with zero config", but the following hardening avoids committing secrets or exposing unnecessary endpoints:

- **No secrets in repo**: `docker-compose.yml` credentials (`POSTGRES_PASSWORD` / `ORDER_DB_PASSWORD`) and Grafana admin (`GF_SECURITY_ADMIN_*`) use `${VAR:-default}` interpolation. Real values live in the root `.env` (git-ignored); template in `.env.example` (committed). Zero-config `docker compose up -d --build` still works with defaults.
- **H2 console off by default**: `application.yml` sets `spring.h2.console.enabled: false`; only the local `dev` profile (`application-dev.yml`) enables it. Container/prod deployments override via `SPRING_PROFILES_ACTIVE` and never activate `dev`, keeping the H2 console closed.
- **Actuator exposure narrowed**: by default only `health` / `info` / `prometheus` (the `metrics` detail endpoint is removed); `health` details are `when-authorized`. For prod/external, appending `prod` to `SPRING_PROFILES_ACTIVE` (`application-prod.yml`) exposes only `health`, with `prometheus`/`info` on a separate management port (`9091`, not mapped to the host in compose) scraped by a trusted internal Prometheus.
- **CORS**: `WebConfig` allows only `localhost:5173` / `localhost:3000`; not the `*` + `allowCredentials` dangerous combination.
- **No error-info leakage**: `OrderNotFoundException` message omits the order id; `404` does not reveal resource existence.

> Note: `admin/admin` for Grafana and `order`/`order` are localhost defaults, not real secrets. Before publishing the repo, put real passwords in `.env` and never hardcode them in compose.

---

See [ARCHITECTURE.md](./ARCHITECTURE.md) for design goals, deployment topology, layered backend/frontend architecture, core-flow sequence diagrams, data model, cross-cutting concerns, and key design decisions.

## Related Articles
- [Evolution of a Distributed Order System: How Spring Boot + React Survived High Concurrency from a Monolith](https://erishen.cn/spring_order-en/)
