import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { EventView, InstanceInfo, KafkaStatus, ConsumerProjection, User } from '../types';

interface MetricSummary {
  totalRequests: number;
  errorRequests: number;
  avgLatencyMs: number;
  circuitBreakerCalls: number;
}

function parsePrometheus(text: string): MetricSummary {
  let totalRequests = 0;
  let errorRequests = 0;
  let sumLatency = 0;
  let circuitBreakerCalls = 0;

  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || !trimmed) continue;

    // http_server_requests_seconds_count{...} 12.0
    if (trimmed.startsWith('http_server_requests_seconds_count')) {
      const val = parseFloat(trimmed.split(/\s+/)[1] ?? '0');
      totalRequests += Number.isFinite(val) ? val : 0;
      if (/status="(4\d\d|5\d\d)"/.test(trimmed)) {
        errorRequests += Number.isFinite(val) ? val : 0;
      }
    } else if (trimmed.startsWith('http_server_requests_seconds_sum')) {
      const val = parseFloat(trimmed.split(/\s+/)[1] ?? '0');
      sumLatency += Number.isFinite(val) ? val : 0;
    } else if (trimmed.startsWith('resilience4j_circuitbreaker_calls_total')) {
      const val = parseFloat(trimmed.split(/\s+/)[1] ?? '0');
      circuitBreakerCalls += Number.isFinite(val) ? val : 0;
    }
  }

  const avgLatencyMs = totalRequests > 0 ? (sumLatency / totalRequests) * 1000 : 0;
  return { totalRequests, errorRequests, avgLatencyMs, circuitBreakerCalls };
}

export function StatusPage() {
  const [instance, setInstance] = useState<InstanceInfo | null>(null);
  const [metrics, setMetrics] = useState<MetricSummary | null>(null);
  const [events, setEvents] = useState<EventView[]>([]);
  const [kafka, setKafka] = useState<KafkaStatus | null>(null);
  const [projection, setProjection] = useState<ConsumerProjection | null>(null);
  const [userMap, setUserMap] = useState<Record<string, User>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [live, setLive] = useState(true);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const refresh = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const [info, promText, evts, kafkaStatus] = await Promise.all([
        api.getInstanceInfo(),
        api.getPrometheus(),
        api.getEvents(),
        api.getKafkaStatus().catch(() => null),
      ]);
      setInstance(info);
      setMetrics(parsePrometheus(promText));
      // 防御性排序：按 createdAt 倒序（最新事件在最上），与后端排序保持一致
      setEvents(
        [...evts].sort((a, b) => {
          const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
          const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
          return tb - ta;
        }),
      );
      setKafka(kafkaStatus);
      setUpdatedAt(new Date());

      // 消费者投影单独请求：失败时保留上一帧数据，避免静默刷新偶发失败把
      // 已加载的「累计消费 / 最近消费时间」清空（kafka 未起本就无投影，静默跳过）。
      try {
        const consumerProj = await api.getConsumerProjection();
        setProjection(consumerProj);
      } catch {
        // silent 模式下保留旧值；非 silent 首帧失败也不强行报错
      }
    } catch (err: unknown) {
      if (!silent) {
        const e = err as { message?: string; status?: number };
        setError(`加载运行态失败：${e.message || e.status || '未知错误'}`);
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  // 加载用户表，用于消费者投影把 userId 还原成姓名，与下单表单/订单列表一致
  useEffect(() => {
    api.getUsers().then((list) => setUserMap(Object.fromEntries(list.map((u) => [u.id, u])))).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // 运行态实时监控：默认每 3 秒静默刷新（不闪 loading 占位），可暂停
  useEffect(() => {
    if (!live) return;
    const timer = setInterval(() => {
      refresh(true);
    }, 3000);
    return () => clearInterval(timer);
  }, [live, refresh]);

  return (
    <div className="status-page">
      <div className="status-head">
        <div>
          <h2>运行态</h2>
          {updatedAt && <p className="status-updated">最近更新 {updatedAt.toLocaleTimeString()}</p>}
        </div>
        <div className="status-actions">
          <button
            className={`live-toggle ${live ? 'on' : ''}`}
            onClick={() => setLive((v) => !v)}
            title={live ? '每 3 秒自动刷新，点击暂停' : '已暂停，点击恢复实时'}
          >
            <span className="live-dot" />
            {live ? '实时' : '已暂停'}
          </button>
          <button className="btn btn-ghost" onClick={() => refresh()} disabled={loading}>
            {loading ? '加载中...' : '刷新'}
          </button>
        </div>
      </div>

      {error && <div className="form-error">{error}</div>}

      <div className="status-grid">
        <div className="card status-card">
          <h3>当前响应副本</h3>
          <p className="instance-id">{instance?.instanceId ?? (loading ? '加载中...' : '—')}</p>
          <p className="instance-app">{instance?.app ?? 'spring-order'}</p>
          <p className="hint">网关轮询两副本，多次刷新可见不同实例（P4 负载均衡）。</p>
        </div>

        <div className="card status-card">
          <h3>HTTP 指标</h3>
          {metrics ? (
            <ul className="metric-list">
              <li><span>总请求数</span><b>{metrics.totalRequests}</b></li>
              <li><span>错误请求 (4xx/5xx)</span><b>{metrics.errorRequests}</b></li>
              <li><span>平均延迟</span><b>{metrics.avgLatencyMs.toFixed(1)} ms</b></li>
              <li><span>熔断器调用</span><b>{metrics.circuitBreakerCalls}</b></li>
            </ul>
          ) : (
            <p className="status-loading-line">{loading ? '加载中...' : '—'}</p>
          )}
        </div>

        <div className="card status-card">
          <h3>Kafka 状态</h3>
          {kafka ? (
            <ul className="metric-list">
              <li>
                <span>连接状态</span>
                <span className={`status-badge ${kafka.connected ? 'ok' : kafka.configured ? 'warn' : 'off'}`}>
                  <span className="dot" />
                  {kafka.connected ? '已连接' : kafka.configured ? '未连接' : '未配置'}
                </span>
              </li>
              <li>
                <span>宿主机 listener</span>
                <b className="kafka-addr">{kafka.bootstrapServers || '—'}</b>
              </li>
              <li>
                <span>容器内 listener</span>
                <b className="kafka-addr">{kafka.internalBootstrapServers || '—'}</b>
              </li>
            </ul>
          ) : (
            <p className="status-loading-line">{loading ? '加载中...' : '—'}</p>
          )}
          <p className="hint">
            {kafka?.connected
              ? 'Outbox 事件将被中继发布到 Kafka（已发布）。两个 listener：宿主机 localhost:9094 供本机后端直连，容器内 kafka:9092 供 docker 编排的 order-svc 副本使用。'
              : kafka?.configured
                ? 'Kafka 未运行，事件保持「待发布」'
                : '未启用 kafka profile，事件保持「待发布」'}
          </p>
        </div>
      </div>

      <div className="card status-card">
        <h3>最近 Outbox 订单事件（事务 Outbox → Kafka order-events）</h3>
        {events.length === 0 ? (
          <p className="hint">暂无事件——下单后这里会出现 OrderCreated 事件</p>
        ) : (
          <ul className="event-stream">
            {events.map((e) => {
              const created = e.createdAt ? new Date(e.createdAt) : null;
              const published = e.publishedAt ? new Date(e.publishedAt) : null;
              const latencySec =
                created && published
                  ? Math.max(0, Math.round((published.getTime() - created.getTime()) / 1000))
                  : null;
              const isPublished = e.status === 'PUBLISHED';
              const consumed = !!projection?.consumedOrderIds.includes(e.aggregateId);
              const consumedAt = projection?.recentOrders.find((o) => o.orderId === e.aggregateId)?.consumedAt;
              return (
                <li key={e.id} className="event-item">
                  <div className="event-head">
                    <span className="event-type">{e.eventType}</span>
                    <span className="event-agg">聚合 {e.aggregateId}</span>
                  </div>
                  <ol className="event-timeline">
                    <li className="tl-step done">
                      <span className="tl-dot" />
                      <div className="tl-body">
                        <span className="tl-title">写入 Outbox</span>
                        <span className="tl-time">{created ? created.toLocaleString() : '—'}</span>
                      </div>
                    </li>
                    <li className={`tl-step ${isPublished ? 'done' : 'pending'}`}>
                      <span className="tl-dot" />
                      <div className="tl-body">
                        <span className="tl-title">发布到 Kafka</span>
                        <span className="tl-time">
                          {isPublished
                            ? published
                              ? published.toLocaleString()
                              : '—'
                            : '等待中继（未连接 Kafka / 待轮询）'}
                        </span>
                      </div>
                    </li>
                    <li className={`tl-step ${consumed ? 'done' : 'pending'}`}>
                      <span className="tl-dot" />
                      <div className="tl-body">
                        <span className="tl-title">被消费 · read-model 投影</span>
                        <span className="tl-time">
                          {consumed
                            ? consumedAt
                              ? new Date(consumedAt).toLocaleString()
                              : '已消费'
                            : projection
                              ? '等待消费者处理'
                              : '消费者未激活（未启用 kafka）'}
                        </span>
                      </div>
                    </li>
                  </ol>
                  <div className="event-foot">
                    <span className={`event-status ${isPublished ? 'status-published' : 'status-pending'}`}>
                      {isPublished ? '已发布' : '待发布'}
                    </span>
                    {latencySec !== null && (
                      <span className="event-latency">链路耗时 {latencySec}s</span>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div className="card status-card">
        <h3>消费者视图 · read-model 投影（OrderEventLogger 消费产出）</h3>
        {projection ? (
          <>
            <ul className="metric-list">
              <li><span>累计消费事件</span><b className="proj-count">{projection.totalConsumed}</b></li>
              <li><span>最近消费时间</span><b className="kafka-addr">{projection.lastConsumedAt ? new Date(projection.lastConsumedAt).toLocaleString() : '—'}</b></li>
            </ul>
            {projection.recentOrders.length === 0 ? (
              <p className="hint">尚无消费记录——下单后事件经 Kafka 被消费者投影到这里</p>
            ) : (
              <ul className="proj-list">
                {projection.recentOrders.map((o) => (
                  <li key={o.orderId} className="proj-item">
                    <span className="proj-order">订单 {o.orderId}</span>
                    <span className="proj-user">用户 {userMap[o.userId]?.name ?? o.userId}</span>
                    <span className="proj-amount">¥{o.finalAmount ?? o.amount ?? '—'}</span>
                    <span className="proj-time">{o.consumedAt ? new Date(o.consumedAt).toLocaleString() : '—'}</span>
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : (
          <p className="status-loading-line">{loading ? '加载中...' : '消费者未激活（未启用 kafka profile / Kafka 未运行），暂无投影'}</p>
        )}
      </div>
    </div>
  );
}
