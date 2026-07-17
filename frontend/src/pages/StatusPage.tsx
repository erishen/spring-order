import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { EventView, InstanceInfo } from '../types';

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
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      const [info, promText, evts] = await Promise.all([
        api.getInstanceInfo(),
        api.getPrometheus(),
        api.getEvents(),
      ]);
      setInstance(info);
      setMetrics(parsePrometheus(promText));
      setEvents(evts);
    } catch (err: unknown) {
      const e = err as { message?: string; status?: number };
      setError(`加载运行态失败：${e.message || e.status || '未知错误'}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <div className="status-page">
      <div className="status-head">
        <h2>运行态</h2>
        <button className="btn btn-ghost" onClick={refresh} disabled={loading}>
          {loading ? '加载中...' : '刷新'}
        </button>
      </div>

      {error && <div className="form-error">{error}</div>}

      <div className="status-grid">
        <div className="card status-card">
          <h3>当前响应副本</h3>
          <p className="instance-id">{instance?.instanceId ?? '—'}</p>
          <p className="instance-app">{instance?.app ?? 'order-platform'}</p>
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
            <p className="hint">—</p>
          )}
        </div>
      </div>

      <div className="card status-card">
        <h3>最近 Kafka 订单事件（Outbox → order-events）</h3>
        {events.length === 0 ? (
          <p className="hint">暂无已发布事件</p>
        ) : (
          <ul className="event-stream">
            {events.map((e) => (
              <li key={e.id} className="event-item">
                <span className="event-type">{e.eventType}</span>
                <span className="event-agg">聚合 {e.aggregateId}</span>
                <span className="event-status">{e.status}</span>
                <span className="event-time">
                  {e.publishedAt ? new Date(e.publishedAt).toLocaleString() : '-'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
