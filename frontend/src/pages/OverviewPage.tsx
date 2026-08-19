import { Link } from 'react-router-dom';

interface Stage {
  id: string;
  title: string;
  problem: string;
  solution: string;
  stack: string[];
}

interface Capability {
  name: string;
  stage: string;
  summary: string;
  details: string[];
}

const STAGES: Stage[] = [
  {
    id: 'P0',
    title: '持久化地基',
    problem: '内存存储重启即丢、读后判断导致超卖',
    solution: 'Spring Data JPA + Flyway 落库，库存用 @Version 乐观锁杜绝并发超卖',
    stack: ['Spring Data JPA', 'Flyway', 'H2 / Postgres'],
  },
  {
    id: 'P1',
    title: '分布式锁 + 缓存',
    problem: '单实例并发上限 = 单机',
    solution: 'Redis SET NX PX + Lua 原子解锁串行化扣减，读路径加缓存',
    stack: ['Redis 分布式锁', 'Redis 缓存'],
  },
  {
    id: 'P2',
    title: '可靠性：幂等 + 限流熔断',
    problem: '网关重试会重复下单、流量突增打挂服务',
    solution: 'Idempotency-Key 幂等表 + Resilience4j 限流 / 熔断 / 重试',
    stack: ['幂等', '限流', '熔断', '重试'],
  },
  {
    id: 'P3',
    title: '事件驱动解耦',
    problem: '下单与下游（库存 / 支付 / 通知）同步耦合',
    solution: 'Kafka + 事务 Outbox，落库与发消息原子、at-least-once 投递',
    stack: ['Kafka', '事务 Outbox'],
  },
  {
    id: 'P4',
    title: '多实例 + 可观测',
    problem: '单入口、不可观测',
    solution: 'nginx 网关负载均衡 + Prometheus / Grafana 监控',
    stack: ['nginx', 'Prometheus', 'Grafana'],
  },
];

const CAPABILITIES: Capability[] = [
  {
    name: '防超卖',
    stage: 'P0',
    summary: '库存扣减用数据库乐观锁，并发下单不超卖',
    details: [
      'InventoryService.reserve() 用 @Transactional + @Version',
      '冲突抛 OptimisticLockingFailureException，重试最多 5 次而非超卖',
      '最终冲突映射为 409 Conflict',
    ],
  },
  {
    name: '分布式锁',
    stage: 'P1',
    summary: 'Redis 锁跨实例串行化同一库存扣减',
    details: [
      'SET lock:{stockId} NX PX 抢占 + Lua 原子解锁',
      '解锁校验 token，避免误删他人锁',
      '取不到锁降级为 DB 乐观锁，仍不超卖',
    ],
  },
  {
    name: '读缓存',
    stage: 'P1',
    summary: '热数据走 Redis，写时失效',
    details: [
      'RedisCacheManager JSON 序列化、5min TTL',
      'getOrder / getAllOrders 加 @Cacheable',
      'createOrder 加 @CacheEvict(orders-all) 失效列表',
    ],
  },
  {
    name: '幂等下单',
    stage: 'P2',
    summary: '重复请求不重复下单',
    details: [
      'Idempotency-Key 头 + idempotency_keys 表（零外部依赖）',
      '命中已完成记录回放首次响应（200）',
      '并发进行中返回 409，业务失败释放 key 可重试',
    ],
  },
  {
    name: '限流',
    stage: 'P2',
    summary: '流量超限直接拒绝',
    details: [
      '@RateLimiter（实例 orderCreate，默认 10/s）',
      '超限抛 RequestNotPermitted → 429',
    ],
  },
  {
    name: '熔断',
    stage: 'P2',
    summary: '下游故障快速失败，防止雪崩',
    details: [
      '@CircuitBreaker COUNT_BASED，失败率 50% 开闸 5s',
      '开闸返回 CallNotPermittedException → 503',
      '预期业务异常（如库存不足）不计入失败率',
    ],
  },
  {
    name: '事务 Outbox',
    stage: 'P3',
    summary: '事件与订单原子落库，消息不丢',
    details: [
      '事件与订单在同一个 DB 事务写入',
      'OutboxRelay 定时扫描 PENDING → 发 Kafka → 标 PUBLISHED',
      'at-least-once：崩溃重发，下游以 orderId 幂等',
    ],
  },
  {
    name: '负载均衡',
    stage: 'P4',
    summary: '多副本分摊流量',
    details: [
      'nginx upstream 轮询两副本',
      '被动健康检查：3 次失败踢出 30s',
      '前端运行态页可见 instanceId 交替',
    ],
  },
  {
    name: '可观测',
    stage: 'P4',
    summary: '指标采集 + 监控看板',
    details: [
      'Actuator + Micrometer 暴露 /actuator/prometheus',
      'Prometheus 抓两副本，Grafana 按 instance 拆分',
      'InstanceInfoContributor 暴露 instanceId 验证轮询',
    ],
  },
];

const TECH_STACK = [
  'Spring Boot 3',
  'Java 17',
  'React 18',
  'Vite 5',
  'Postgres',
  'Redis',
  'Kafka',
  'Resilience4j',
  'nginx',
  'Prometheus',
  'Grafana',
];

export function OverviewPage() {
  return (
    <div className="overview-page">
      {/* Hero */}
      <section className="overview-hero">
        <h2>高并发分布式订单平台</h2>
        <p className="overview-subtitle">
          一个从单体逐步演进为高并发分布式系统的订单平台——下单、促销、幂等、事件驱动、多实例监控，每个痛点对应一个演进阶段。
        </p>
        <div className="tech-badges">
          {TECH_STACK.map((t) => (
            <span key={t} className="tech-badge">{t}</span>
          ))}
        </div>
      </section>

      {/* 快速入口 */}
      <section>
        <h3 className="section-title">功能入口</h3>
        <div className="quick-grid">
          <Link to="/orders" className="quick-card">
            <h4>下订单</h4>
            <p>下单 + 促销规则 + 幂等，实时查看优惠明细与订单列表</p>
          </Link>
          <Link to="/users" className="quick-card">
            <h4>用户</h4>
            <p>预置用户列表，验证新用户首单优惠判定</p>
          </Link>
          <Link to="/status" className="quick-card">
            <h4>运行态</h4>
            <p>当前响应副本、HTTP 指标、Kafka 事件流实时监控</p>
          </Link>
        </div>
      </section>

      {/* 架构演进 */}
      <section>
        <h3 className="section-title">架构演进（P0 → P4）</h3>
        <div className="stage-timeline">
          {STAGES.map((s) => (
            <div key={s.id} className="stage-item">
              <div className="stage-marker">
                <span className="stage-badge">{s.id}</span>
              </div>
              <div className="stage-content">
                <div className="stage-head">
                  <h4>{s.title}</h4>
                  <div className="stage-stack">
                    {s.stack.map((t) => (
                      <span key={t} className="tech-badge tech-badge-sm">{t}</span>
                    ))}
                  </div>
                </div>
                <p className="stage-problem">
                  <span className="stage-label">痛点</span>
                  {s.problem}
                </p>
                <p className="stage-solution">
                  <span className="stage-label">方案</span>
                  {s.solution}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 核心能力 */}
      <section>
        <h3 className="section-title">核心能力</h3>
        <div className="capability-grid">
          {CAPABILITIES.map((c) => (
            <div key={c.name} className="capability-card">
              <div className="capability-head">
                <span className="capability-name">{c.name}</span>
                <span className="capability-stage">{c.stage}</span>
              </div>
              <p className="capability-desc">{c.summary}</p>
              <ul className="capability-details">
                {c.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
