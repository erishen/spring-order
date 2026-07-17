# Order Platform · 订单平台

> 面试演示项目（Interview Demo）— 订单管理 + 促销规则演示，并正逐步演进为「高并发分布式订单平台」。
> 后端 Spring Boot + Spring Data JPA（Flyway 迁移、库存 `@Version` 乐观锁，默认内嵌 H2、可切 Postgres）；前端 React/Vite。
>
> English version: see [English](#english)

---

## 1. 项目简介

`order-platform` 是一个用于演示典型后端业务场景、并**逐步向高并发分布式架构演进**的项目：下单、促销规则计算、用户列表、订单查询、库存预留。
**P0 阶段已落地持久化地基**：数据通过 Spring Data JPA 落库（默认内嵌 H2 文件库、零外部依赖、重启不丢；亦可通过 `postgres` profile 切真实 Postgres），Flyway 管理表结构，库存扣减走 `inventory` 表 + `@Version` 乐观锁，从数据库层杜绝并发超卖。

重点演示的能力：

- 下单流程 + 业务规则（促销 / 库存校验）
- 前后端**统一响应契约**（`{ order, promotion }` 信封）
- 异常处理与 HTTP 语义（400 / 404）
- 单元测试 + 集成测试 + JaCoCo 覆盖率门禁

## 2. 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.2.5 · Java 17 · Maven · Lombok |
| 前端 | React 18 · TypeScript · Vite 5 · React Router 6 |
| 开发态 Mock | MSW 2.4（Mock Service Worker） |
| 测试 | JUnit 5 · Mockito · `@WebMvcTest` / `@SpringBootTest` · JaCoCo（覆盖率门禁 80%） |
| 存储 | Spring Data JPA + Flyway + H2（文件型，零依赖）/ PostgreSQL（`postgres` profile）· 库存 `@Version` 乐观锁 |
| 迁移 | Flyway（`V1__init.sql`，H2 / PostgreSQL 兼容） |

包命名空间：`com.example.order`（Java 示例占位约定，未作改动）。

## 3. 目录结构

```
order-platform/
├── Makefile                 # 常用命令封装（install / run / test / ci ...）
├── backend/                 # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/example/order/
│       ├── controller/      # OrderController, UserController
│       ├── service/         # OrderService, PromotionService
│       ├── dto/             # 请求/响应模型（含促销信封）
│       ├── model/           # Order, Inventory, User, CartItem
│       ├── exception/       # 全局异常处理 + 自定义异常（含乐观锁冲突 409）
│       └── resources/
│           ├── application.yml            # 默认：内嵌 H2 文件库 + Flyway + H2 Console
│           ├── application-postgres.yml   # postgres profile
│           └── db/migration/V1__init.sql  # 建 orders / inventory 表 + 种子库存
└── frontend/                # React/Vite 前端
    ├── src/
    │   ├── api/client.ts    # fetch 封装
    │   ├── mocks/           # MSW handlers / server / browser
    │   ├── components/      # OrderForm, UserList
    │   ├── pages/           # OrderPage, UsersPage
    │   └── types/index.ts   # 前端类型（与后端契约对齐）
    ├── vite.config.ts       # 端口 3000 + /api 代理 8080
    └── package.json
```

## 4. 快速开始

### 前置要求

- Java 17+、Maven 3.8+
- Node 18+（推荐 22）

### 启动后端

```bash
cd backend
mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

启动后监听 **http://localhost:8080**。

> ⚠️ 本机环境请用 `-o`（离线模式）并显式锁定端口，详见 [§7 注意事项](#7-注意事项本机环境)。

### 启动前端

```bash
cd frontend
npm install
npm run dev          # Vite，默认端口 3000，/api 代理到 8080
```

浏览器打开 **http://localhost:3000**。

> 前端在 **开发模式（DEV）默认走 MSW Mock**，UI 自包含、无需后端即可浏览演示数据。
> 若要对接真实后端，关闭 MSW（见 [§6](#6-前端-mockmsw)）或设置 `VITE_API_BASE_URL`。

### 一条命令（Makefile）

```bash
make install         # 后端 mvn clean install + 前端 npm install
make run             # 仅后端（= backend-run，见注意事项）
make test            # 后端测试
make ci              # 后端测试 + JaCoCo 覆盖率门禁（模拟 CI）
```

## 5. API 参考

基础路径：`http://localhost:8080`

### 5.1 用户列表

```
GET /api/users
```

返回 5 个预置用户（`@example.com`）。

```json
[
  { "id": "u1", "name": "Alice Johnson", "email": "alice@example.com" },
  { "id": "u2", "name": "Bob Smith", "email": "bob@example.com" }
]
```

### 5.2 创建订单

```
POST /api/orders
```

请求体（`OrderCreateRequest`）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | string | ✅ | 非空（`@NotBlank`） |
| `amount` | number | ✅ | 正数（`@Positive`） |
| `stockId` | string | ❌ | 库存池 id，默认 `"DEFAULT"`（由服务端 `inventory` 表托管） |
| `requiredStock` | int | ❌ | 本次扣减库存数，默认 `1` |

**新用户判定**：若该 `userId` 在数据库（`orders` 表）中无任何历史订单，则视为新用户（首单）。
**库存校验**：由 `InventoryService` 基于 `inventory` 表 + `@Version` 乐观锁完成；不足时返回 `400 Insufficient Stock`。旧契约里的 `availableStock` 客户端字段已移除——可用库存改为服务端托管状态。

成功返回 **201 Created**，`Location` 头指向新订单，响应体为 `{ order, promotion }` 信封：

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

### 5.3 查询订单

```
GET /api/orders/{id}        # 单个订单，不存在返回 404
GET /api/orders             # 订单列表
```

### 5.4 错误码

| HTTP | 场景 | 说明 |
|------|------|------|
| 400 | 参数校验失败 / 库存不足 | 校验注解报错或 `InsufficientStockException` |
| 404 | 订单不存在 | `OrderNotFoundException`（消息不含 id，避免信息泄露） |

## 6. 促销规则（Q6）

`PromotionService.applyPromotion(amount, isNewUser, requiredStock, availableStock)` 实现以下规则：

| 规则 | 条件 | 优惠 |
|------|------|------|
| Rule 1 新用户首单 | `isNewUser == true` | `-10` |
| Rule 2 满减 | `amount >= 100` | `-20` |
| Rule 3 不可叠加（满减优先） | 同时满足 Rule 1 & 2 | 仅取 Rule 2（满减优先） |
| Rule 4 库存校验 | `requiredStock > availableStock` | 抛 `InsufficientStockException` → 400（冗余防御层；真实拦截在 `InventoryService`） |

计算顺序（在 `amount > 0` 前提下）：

1. **先校验库存**（Rule 4，冗余防御层）——真实拦截在 `InventoryService`（`inventory` 表 + `@Version` 乐观锁）；
2. 若 `amount >= 100` → 满减 `-20`（Rule 2，优先）；
3. 否则若新用户 → `-10`（Rule 1）；
4. 否则无优惠。

> `amount <= 0` 视为非法输入，返回无折扣；新用户优惠若使金额转负则兜底为 `0`。

## 7. 注意事项（本机环境）

> 以下两点是**本机环境的实际坑**，非代码缺陷。若在能直连 Maven 中央仓库的机器上，`mvn spring-boot:run` 可直接运行。

### 7.1 Maven 需离线模式 `-o`

本机 `~/.m2/settings.xml` 配置了阿里云镜像，但沙箱对该镜像限速极严（部分依赖仅数百 B/s），
在线 `mvn spring-boot:run` 会卡在下载元数据、长时间无进展。本地实际已缓存全部所需依赖，
因此用 **离线模式** 可秒起：

```bash
mvn -o spring-boot:run
```

> Makefile 里的 `backend-run` 是明文 `mvn spring-boot:run`（无 `-o`），在本机会卡住；
> 本机请改用上面的离线命令，或自行给 Makefile 加上 `-o`。

### 7.2 端口需显式锁定 8080

`application.yml` 写明 `server.port: 8080`，但本机 `mvn spring-boot:run` 实测曾绑定到**随机端口**
（如 61705），原因未完全定位（非环境变量 / pom 插件 / 编译产物所致）。
命令行参数优先级最高，故显式锁定最稳：

```bash
mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

### 7.3 前端端口冲突

`frontend/vite.config.ts` 默认 `port: 3000` 且代理 `/api → 8080`。若 3000 被其它服务占用，
用其他端口启动即可，例如：

```bash
cd frontend && npx vite --port 3001
```

## 8. 前端 Mock（MSW）

`src/main.tsx` 在 `import.meta.env.DEV` 下启动 MSW Worker，开发态默认拦截 `/api/*` 返回模拟数据，
UI 因此**无需后端也能独立演示**。Mock 的促销语义与后端保持一致（满 100 减 20 优先、新用户减 10）。

- 想对接真实后端：临时禁用 MSW（去掉 `main.tsx` 中 `worker.start(...)` 调用），或设置环境变量
  `VITE_API_BASE_URL=http://localhost:8080`，并让请求绕过 Mock。
- `onUnhandledRequest: 'bypass'` 已配置，未被 Mock 覆盖的请求会正常发往网络（经 Vite 代理到 8080）。

## 9. 测试与覆盖率

```bash
make backend-test            # 运行后端测试（controller / service / promotion / integration）
make backend-coverage        # 生成 JaCoCo 报告（target/site/jacoco/index.html）
make backend-coverage-check  # 校验覆盖率是否达到 80% 门禁
make ci                      # = test + coverage-check（模拟 CI 流水线，Q8）
```

测试覆盖：下单成功（201）、新用户推导、已满减优先、库存不足（400）、订单不存在（404）、响应契约对齐等。

> 运行后端会在 `backend/data/` 生成 H2 文件库（已加入 `.gitignore`，不入库）。可用浏览器访问 **http://localhost:8080/h2-console** 直接查看 `orders` / `inventory` 表。

## 10. 架构演进（分布式方向）

`order-platform` 正从单实例演示演进为**高并发分布式订单平台**。当前痛点与演进目标一一对应：

| 当前形态 | 分布式目标 | 落地阶段 |
|----------|------------|----------|
| `ConcurrentHashMap` 内存存储，重启即丢 | 共享持久化（Postgres）+ 多实例无状态 | ✅ **P0 已实现** |
| 库存读后判断（超卖风险） | DB 乐观锁（`@Version`）+ 重试，杜绝超卖 | ✅ **P0 已实现** |
| 无幂等，网关重试会重复下单 | `Idempotency-Key` 幂等 + Resilience4j 限流/熔断 | 🔜 P2 规划 |
| 单实例，并发=单机 | 多实例 + Redis 分布式锁/缓存 + API 网关 | ✅ **P1 已实现** |
| 下单同步耦合 | 订单事件上 Kafka（Outbox 模式）解耦库存/支付/通知 | 🔜 P3 规划 |

### P0 · 持久化地基（当前已落地）

- **存储**：Spring Data JPA；默认内嵌 **H2 文件库**（零外部依赖，重启不丢），通过 `postgres` profile 可切真实 Postgres。
- **迁移**：Flyway 管理 schema（`V1__init.sql`，H2/Postgres 兼容），Hibernate `ddl-auto: none`。
- **防超卖**：库存存于 `inventory` 表，`InventoryService.reserve()` 用 `@Transactional` + `@Version` 乐观锁；并发冲突抛 `OptimisticLockingFailureException`，`OrderService` 带重试循环（最多 5 次）重试而非超卖。乐观锁冲突经 `GlobalExceptionHandler` 映射为 **409 Conflict**。
- **切换 Postgres**（需本地 Docker）：
  ```bash
  # 根目录 docker-compose.yml 起 Postgres 16
  docker compose up -d db
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=postgres"
  ```

### P1 · Redis 分布式锁 + 缓存（当前已落地）

让 order-platform 从「单实例」走向「多实例无状态横向扩容」：

- **分布式锁**：新增 `RedisLockService`（SET `lock:{stockId}` NX PX + Lua 原子解锁）。`OrderService.reserveWithRetry` 在 Redis 可用时，用锁把同一 `stockId` 的库存扣减串行化，跨实例消除乐观锁重试风暴；未获取锁时降级为直接执行（DB 乐观锁仍兜底，不超卖）。Lua 解锁只在 token 仍匹配时删 key，避免误删他人锁。
- **缓存**：`RedisConfig`（`@ConditionalOnProperty(spring.data.redis.host)` + `@EnableCaching`）配 `RedisCacheManager`（JSON 序列化、5min TTL）。`OrderService.getOrder` / `getAllOrders` 加 `@Cacheable`，`createOrder` 加 `@CacheEvict(orders-all)`——热订单查询走 Redis，降 DB 压力。
- **优雅降级（关键）**：Redis 是**可选基础设施**。仅在 `redis` profile 下才启用锁 + 缓存；默认/无 Redis 时 `RedisLockService` 与 `@EnableCaching` 根本不加载，`OrderService` 退回 P0 的纯 DB 乐观锁 + 无缓存行为，**本地零依赖即可运行与测试**。
- **启用方式**（需本地 Docker）：
  ```bash
  # 根目录 docker-compose.yml 同时起 Postgres 16 + Redis 7
  docker compose up -d
  # 多实例：都连同一 Redis + Postgres，端口不同
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=redis,postgres"
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --spring.profiles.active=redis,postgres"
  ```
- **测试**：`RedisLockServiceTest`（mock StringRedisTemplate，纯单元验证加锁/解锁/Lua/降级，默认跑无需真 Redis）；`RedisP1IntegrationTest`（`@ActiveProfiles("redis")`，检测 localhost:6379 可达否则整体 skip，本地有 Docker 时验证锁串行化 + 缓存 bean 装配）。

### 后续路线（规划中，未实现）

- **P2 可靠性**：`POST /api/orders` 加 `Idempotency-Key` 幂等 + Resilience4j 限流/熔断/重试。改动小、面试高频，不依赖外部中间件。
- **P3 事件驱动**：订单创建后发领域事件到 Kafka，解耦库存/支付/通知；用 Outbox 模式保证「落库 + 发消息」原子。需 Docker 跑 Kafka。
- **P4 多实例演示**：`docker-compose` 拉起 2+ order-svc + Redis + Postgres + Kafka + 网关，配 Micrometer / OpenTelemetry 链路追踪，演示水平扩展与压测。

---

## English

### Order Platform — Interview Demo

An order-management + promotion-rules demo, evolving toward a high-concurrency distributed order platform.
Spring Boot backend (Spring Data JPA + Flyway + `@Version` optimistic lock; embedded H2 by default, Postgres via profile), React/Vite frontend.

**Stack:** Spring Boot 3.2.5 · Java 17 · Maven · Lombok · Spring Data JPA · Flyway · H2 / PostgreSQL · React 18 · TypeScript · Vite 5 · React Router 6 · MSW 2.4 · JaCoCo (80% gate).

**Quick start**

```bash
# Backend (use offline mode + explicit port on throttled mirrors)
cd backend && mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"

# Frontend
cd frontend && npm install && npm run dev   # http://localhost:3000, proxies /api -> 8080
```

**API** (base `http://localhost:8080`)

- `GET /api/users` — list of 5 preset users.
- `POST /api/orders` — create order. Body: `{ userId, amount, stockId?, requiredStock? }` (`stockId` defaults to `"DEFAULT"`, server-managed inventory; `availableStock` client field removed).
  Returns `201` with `{ order, promotion }` envelope.
- `GET /api/orders/{id}` — single order (`404` if missing).
- `GET /api/orders` — list all.

**Promotion rules (Q6)** — in `PromotionService.applyPromotion`:

1. New-user first order → `-10`.
2. `amount >= 100` → `-20`.
3. Not stackable; full-reduction (Rule 2) takes priority.
4. Stock check (`requiredStock > available`) → `InsufficientStockException` → `400`. The real enforcement
   lives in `InventoryService` (DB `inventory` table + `@Version` optimistic lock); this is a redundant guard.

A user is "new" when no prior order exists for that `userId` in the database (`orders` table).

**Response contract** — `POST /api/orders` returns:

```json
{
  "order":      { "id": "...", "userId": "...", "amount": 50, "discount": 10, "finalAmount": 40, "status": "CREATED", "createdAt": "..." },
  "promotion":  { "originalAmount": 50, "discount": 10, "finalPrice": 40, "appliedRule": "new_user" }
}
```

`appliedRule` ∈ `new_user` | `full_reduction` | `none`.

**Tests** — `make backend-test` / `make backend-coverage` / `make backend-coverage-check` (80% gate) / `make ci`.

**Env notes** — On this machine: (a) Maven must run with `-o` (offline) because the configured Aliyun
mirror is heavily throttled in the sandbox; (b) `mvn spring-boot:run` may bind a random port instead of
8080, so lock it with `--server.port=8080`; (c) avoid port 3000 clashes by using another Vite port if needed.
