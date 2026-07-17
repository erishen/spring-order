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
| 测试 | JUnit 5 · Mockito · `@WebMvcTest` / `@SpringBootTest` · EmbeddedKafka（`spring-kafka-test`）· JaCoCo（覆盖率门禁 80%） |
| 存储 | Spring Data JPA + Flyway + H2（文件型，零依赖）/ PostgreSQL（`postgres` profile）· 库存 `@Version` 乐观锁 |
| 缓存 / 分布式锁 | Redis（`spring-boot-starter-data-redis`）+ `RedisLockService`（SET NX PX + Lua 解锁）+ `RedisCacheManager`，仅 `redis` profile 启用 |
| 消息 / 事件 | Kafka（`spring-kafka`）+ 事务发件箱 Outbox（`OutboxService` / `OutboxRelay`），仅 `kafka` profile 启用 |
| 可靠性 | Resilience4j（限流 / 熔断 / 重试）+ `Idempotency-Key` 幂等表（JPA，零外部依赖） |
| 可观测 | Actuator + Micrometer（`micrometer-registry-prometheus`），暴露 `/actuator/prometheus` |
| 网关 / 编排 | nginx 负载均衡 + Docker Compose 多实例全栈（`backend/Dockerfile` 多阶段镜像） |
| 迁移 | Flyway：`V1__init.sql`（订单 / 库存）、`V2__idempotency.sql`（幂等键）、`V3__outbox.sql`（发件箱），H2 / PostgreSQL 兼容 |

包命名空间：`com.example.order`（Java 示例占位约定，未作改动）。

## 3. 目录结构

```
order-platform/
├── Makefile                 # 常用命令封装（install / run / test / ci ...）
├── docker-compose.yml       # 全栈编排：order-svc 副本 + redis + postgres + kafka + nginx + prometheus + grafana
├── backend/                 # Spring Boot 后端
│   ├── pom.xml
│   ├── Dockerfile           # 多阶段：maven 构建 → eclipse-temurin JRE
│   └── src/main/java/com/example/order/
│       ├── controller/      # OrderController（含 Idempotency-Key 头）, UserController, EventController
│       ├── service/         # OrderService, PromotionService, InventoryService
│       │                   # RedisLockService, IdempotencyService, OutboxService
│       │                   # OutboxRelay, OrderEventLogger（Kafka 消费示例）
│       ├── dto/             # 请求/响应模型（含促销信封）
│       ├── model/           # Order, Inventory, User, CartItem, IdempotencyRecord, OutboxEvent
│       ├── exception/       # 全局异常处理 + 自定义异常（乐观锁 409 / 幂等冲突 409 / 限流 429 / 熔断 503）
│       └── resources/
│           ├── application.yml            # 默认：H2 文件库 + Flyway + Actuator/Prometheus
│           ├── application-postgres.yml   # postgres profile
│           ├── application-redis.yml      # redis profile（分布式锁 + 缓存）
│           ├── application-kafka.yml      # kafka profile（Outbox 中继 + topic）
│           └── db/migration/
│               ├── V1__init.sql           # 建 orders / inventory 表 + 种子库存
│               ├── V2__idempotency.sql    # 幂等键表
│               └── V3__outbox.sql         # 发件箱事件表
├── conf/nginx.conf          # 网关：多副本轮询 + 被动健康检查，对外 :80
├── prometheus/prometheus.yml# 抓取两副本 /actuator/prometheus
├── grafana/provisioning/... # Prometheus 数据源（admin/admin）
├── scripts/                 # load-test.js (k6) + load-test.py (locust) 压测
└── frontend/                # React/Vite 前端（已容器化，见 P4）
    ├── Dockerfile           # 单阶段：nginx 托管预编译 dist，代理 /api /actuator → 网关
    ├── nginx.conf           # 容器内 nginx：SPA history 回退 + 反代后端
    ├── .dockerignore        # 排除 node_modules/src，仅 COPY dist
    ├── src/
    │   ├── api/client.ts    # fetch 封装（含 getOrders / getEvents / getInstanceInfo / getPrometheus）
    │   ├── mocks/           # MSW handlers / server / browser（仅 DEV）
    │   ├── components/      # OrderForm, UserList
    │   ├── pages/           # OrderPage（下单 + 订单列表）, UsersPage, StatusPage（运行态）
    │   └── types/index.ts   # 前端类型（与后端契约对齐）
    ├── vite.config.ts       # 端口 3000 + /api + /actuator 代理 8080
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
**幂等头（可选）**：`POST /api/orders` 还接受请求头 `Idempotency-Key`（唯一字符串）。同 key 重复提交命中已完成记录→回放首次响应 `200`；并发提交→`409`；业务失败→释放该 key 可安全重试。详见 [§10 P2](#p2--可靠性幂等--resilience4j当前已落地)。

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
| 409 | 乐观锁冲突 / 幂等键进行中 | `OptimisticLockingFailureException`（并发扣库存重试仍冲突）→ 409；`IdempotencyConflictException`（同 key 并发提交）→ 409 |
| 429 | 限流触发 | Resilience4j `@RateLimiter`（`orderCreate` 实例，默认 10/s）→ `RequestNotPermitted` → 429 |
| 503 | 熔断器开闸 | Resilience4j `@CircuitBreaker` 开闸 → `CallNotPermittedException` → 503 |

### 5.5 订单事件流（P3 Outbox）

```
GET /api/events        # 最近已发布（PUBLISHED）的 outbox 事件，最多 20 条
```

返回经 Outbox 中继投递到 Kafka `order-events` topic 的事件，前端「运行态」页用它渲染事件流：

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

### 5.6 运行态信息（P4）

```
GET /actuator/info     # 含 instanceId（当前响应副本主机名），演示网关轮询
GET /actuator/prometheus  # 原始指标文本，前端解析 HTTP 请求数 / 延迟 / 熔断调用
```

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
| 单实例，并发=单机 | 多实例 + Redis 分布式锁/缓存 | ✅ **P1 已实现** |
| 无幂等，网关重试会重复下单 | `Idempotency-Key` 幂等 + Resilience4j 限流/熔断/重试 | ✅ **P2 已实现** |
| 下单同步耦合 | 订单事件上 Kafka（Outbox 模式）解耦库存/支付/通知 | ✅ **P3 已实现** |
| 单入口 + 可观测 | nginx 网关负载均衡 + Actuator/Prometheus/Grafana 监控 | ✅ **P4 已实现** |

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

### P2 · 可靠性：幂等 + Resilience4j（当前已落地）

让订单接口在「网络重试 / 网关重发 / 流量突增」下依然安全：

- **幂等（`Idempotency-Key`）**：`POST /api/orders` 接受可选请求头 `Idempotency-Key`。服务端用 `idempotency_keys` 表（JPA 持久化，零外部依赖）记录每个 key 的状态：
  - 同 key 的**第二次请求命中已完成记录** → 直接回放首次的 `OrderWithPromotion`（**200 OK**），不重复建单、不重复扣库存。
  - 同 key 的并发请求**进行中（IN_PROGRESS）** → 返回 **409 Conflict**，阻止双重提交。
  - 唯一约束 + 并发插入冲突兜底，保证多实例下同一 key 只会被一个请求处理。
  - 业务失败（如库存不足 400）时**释放该 key**，客户端可用同一 key 安全重试（无副作用即不消耗幂等）。
- **限流 / 熔断 / 重试（Resilience4j）**：`OrderService.createOrder` 加三件套注解，实例名 `orderCreate`：
  - `@RateLimiter` 限流（默认 10 次/秒）→ 超限返回 **429 Too Many Requests**。
  - `@CircuitBreaker` 熔断（COUNT_BASED 滑动窗口，失败率 50% 开闸 5s）→ 开闸返回 **503 Service Unavailable**；`InsufficientStockException` 等预期业务异常不计入失败率。
  - `@Retry` 重试（最多 3 次）针对瞬时故障（`OptimisticLockingFailureException` / `DataAccessException`），跳过幂等冲突与库存不足。
  - 三者通过 Spring AOP 织入，`RequestNotPermitted` / `CallNotPermittedException` 由 `GlobalExceptionHandler` 映射为 429 / 503。
- **测试**：`IdempotencyServiceTest`（PROCEED / HIT / IN_PROGRESS / 并发插入冲突 / fail 全分支单测）；`OrderControllerTest` 新增幂等分支（HIT 回放不调 service、IN_PROGRESS 返 409、PROCEED 调 complete）；`IdempotencyIntegrationTest` 真实验证「同 key 只建单一次 + 库存只扣一次」；`RateLimitIntegrationTest`（调小 limit 验证 429）。

### P3 · 事件驱动：Kafka + 事务发件箱 Outbox（当前已落地）

让下单与下游（库存/支付/通知/审计）彻底解耦，且保证「落库 + 发消息」原子：

- **事务发件箱（Transactional Outbox）**：`OrderService.createOrder` 在**同一个 DB 事务**里写完 `orders` 行后，立刻往 `outbox_events` 表写一条 `OrderCreated` 事件（`saveEvent` 用 REQUIRED，与订单同提交）。这样「状态已更新」和「事件已入队」永远不会不一致——要么都成功，要么都回滚。
- **中继（OutboxRelay）**：`@ConditionalOnProperty(spring.kafka.bootstrap-servers)` 的定时任务（`fixedDelay` 默认 1s）扫描 `PENDING` 行，逐条 `kafkaTemplate.send` 到 `order-events` topic，成功后再 `markPublished`（REQUIRES_NEW 独立提交）。**at-least-once 投递**：若中继在「发送成功→标记完成」之间崩溃，该事件会被下次轮询重发——因此下游**必须幂等**（正好与 P2 的 `Idempotency-Key` 呼应）。发送失败的行保持 `PENDING`，下次重试，**事件绝不丢失**，即使 Kafka 临时宕机。
- **优雅降级（关键）**：Kafka 是**可选基础设施**。仅在 `kafka` profile 下才连 broker、装配 `KafkaConfig` / `OutboxRelay` / 示例消费者 `OrderEventLogger`；默认/无 Kafka 时这些 Bean 根本不创建，`outbox_events` 行只是留 `PENDING`（待有 broker 时补发），**本地零依赖即可运行与测试**。
- **下游消费者**：`OrderEventLogger` 是示例消费端（`@KafkaListener` 打印 `order-events`）。真实场景下可驱动履约、发通知、更新读模型等，且都以 `orderId` 为幂等键。
- **启用方式**（需本地 Docker）：
  ```bash
  # 根目录 docker-compose.yml 起 Postgres + Redis + Kafka(KRaft 单节点)
  docker compose up -d
  # 多实例：都连同一 Redis + Postgres + Kafka
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.profiles.active=redis,postgres,kafka"
  mvn -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --spring.profiles.active=redis,postgres,kafka"
  ```
- **测试**：`OutboxServiceTest`（默认 profile 无 Kafka，验证 PENDING 写入 + markPublished 翻转，纯 DB）；`OutboxIntegrationTest`（`@SpringBootTest` + `spring-kafka-test` 的 **EmbeddedKafka** 内嵌真实 broker，无需 Docker，验证 PENDING 行被中继发出、示例消费者收到、行翻 PUBLISHED）。

### P4 · 多实例演示：网关 + 监控（当前已落地）

把 P0–P3 的所有能力在「真正多实例」下串起来，演示水平扩展与可观测性：

- **多实例 + 网关**：根目录 `docker-compose.yml` 一键拉起 **2 个 order-svc 副本**（`order-svc-1` / `order-svc-2`），共享同一 Redis + Postgres + Kafka（对应 P1 锁/缓存、P0 持久化、P3 Outbox），前面由 **nginx 网关**（`conf/nginx.conf`）轮询负载均衡，对外单一入口 `:80`。`backend/Dockerfile` 多阶段镜像化，`docker compose up -d --build` 起全栈。
- **前端 UI 容器**：`frontend/` 已容器化——`frontend/Dockerfile` 用 nginx 托管预编译 `dist`，并把 `/api` 与 `/actuator` 反代到网关（同源、无 CORS）。全栈起来后多一个 `frontend` 服务（默认 `:18083`，避开本机 80/3000 冲突），直接浏览器打开即可用完整 UI 演示 P1–P4：订单页（下单 + 订单列表，列表走 Redis `@Cacheable`）、用户页、**运行态页（`/status`）**——展示当前响应副本（`/actuator/info` 的 `instanceId`，多次刷新可见网关轮询到不同副本）、HTTP 指标（解析 `/actuator/prometheus`）、以及最近 Kafka 订单事件流（`GET /api/events`）。
- **可观测性**：新增 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，`/actuator/prometheus` 暴露 JVM/HTTP/Resilience4j 指标；`prometheus/prometheus.yml` 分别抓取两副本，`grafana/provisioning` 注入 Prometheus 数据源（admin/admin）。Prometheus `targets` 可见两副本被分别抓取，Grafana 按实例标签拆分，直观看到网关把流量摊到两副本。
- **健康指示器坑（已修）**：`data-redis` 在 classpath 上会让 Spring Boot 自动注册 `RedisReactiveHealthIndicator`，**与 `RedisConfig` 的 `@ConditionalOnProperty` 无关**——无 Redis 时它恒 ping `localhost:6379` 失败，导致顶层 `/actuator/health` 误报 `DOWN`（liveness/readiness 不受影响）。已在默认 `application.yml` 关掉 `management.health.redis.enabled`，仅 `redis` profile 开启；无中间件时 health 正确为 `UP`。
- **降级不变**：P4 只加「编排 + 监控」，不改运行时契约。副本仍只在 `redis,postgres,kafka` profile 连中间件；nginx 被动健康检查（3 次失败踢出 30s）保证单副本宕机网关仍可用；Actuator 端点默认暴露，供健康检查与抓取。
- **一键全栈**：
  ```bash
  docker compose up -d --build
  # 网关 :80 / 前端 UI :18083 / prometheus :9090 / grafana :3000
  # 扩容：docker compose up -d --scale order-svc-1=2 --scale order-svc-2=2 --build
  ```
- **压测示例**（`scripts/load-test.js` 用 k6，`scripts/load-test.py` 用 locust）：
  ```bash
  k6 run -e BASE=http://localhost scripts/load-test.js
  # 或
  locust -f scripts/load-test.py --headless -u 50 -r 10 -t 1m -H http://localhost
  ```
  多副本 + 网关让压测 RPS 随副本数近线性提升；kill 单副本后网关自动剔除，流量不中断。

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
