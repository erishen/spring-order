import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { OrderForm, type OrderCreatedInfo } from '../components/OrderForm';
import { api } from '../api/client';
import type { OrderResponse, EventView, User } from '../types';

export function OrderPage() {
  const [info, setInfo] = useState<OrderCreatedInfo | null>(null);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [userMap, setUserMap] = useState<Record<string, User>>({});
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [replaying, setReplaying] = useState(false);
  const [replayError, setReplayError] = useState<string | null>(null);
  const [orderEvent, setOrderEvent] = useState<EventView | null>(null);
  const [clearing, setClearing] = useState(false);
  const [clearError, setClearError] = useState<string | null>(null);

  const ruleLabels: Record<string, string> = {
    new_user: '新用户优惠',
    full_reduction: '满减优惠',
    none: '无优惠',
  };

  const loadOrders = async () => {
    setLoadingOrders(true);
    try {
      const list = await api.getOrders();
      setOrders(list);
    } catch {
      // keep previous list on error
    } finally {
      setLoadingOrders(false);
    }
  };

  // 加载用户表，用于订单列表把 userId 还原成「姓名（邮箱）」，与下单表单保持一致
  const loadUsers = async () => {
    try {
      const list = await api.getUsers();
      setUserMap(Object.fromEntries(list.map((u) => [u.id, u])));
    } catch {
      // 用户表加载失败时回退为直接显示 userId
    }
  };

  useEffect(() => {
    loadOrders();
    loadUsers();
  }, []);

  const handleOrderCreated = (created: OrderCreatedInfo) => {
    setInfo(created);
    setReplayError(null);
    loadOrders();
    loadOrderEvent(created.order.id);
  };

  // 清空所有演示数据：订单 + Outbox 事件 + 幂等键 + 消费者投影（带二次确认）
  const handleClear = async () => {
    if (orders.length === 0) return;
    if (!window.confirm(`确定清空全部 ${orders.length} 笔订单吗？\n（同时清空 Outbox 事件、幂等键与消费者投影，不可恢复）`)) {
      return;
    }
    setClearing(true);
    setClearError(null);
    try {
      await api.deleteOrders();
      setOrders([]);
      setInfo(null);
      setOrderEvent(null);
    } catch (err: unknown) {
      const e = err as { message?: string };
      setClearError(`清理失败：${e.message || '未知错误'}`);
    } finally {
      setClearing(false);
    }
  };

  // 查该订单的 Outbox 事件（OrderCreated），观察 PENDING → PUBLISHED 的中继发布过程
  const loadOrderEvent = async (orderId: string) => {
    try {
      const events = await api.getEvents();
      const evt = events.find((e) => e.aggregateId === orderId);
      setOrderEvent(evt ?? null);
    } catch {
      setOrderEvent(null);
    }
  };

  // 事件未发布时每 2 秒轮询，让「待发布 → 已发布」自动可见（无需手动点刷新）
  useEffect(() => {
    if (!info || orderEvent?.status === 'PUBLISHED') return;
    const timer = setInterval(async () => {
      try {
        const events = await api.getEvents();
        const evt = events.find((e) => e.aggregateId === info.order.id) ?? null;
        setOrderEvent(evt);
      } catch {
        /* 轮询失败静默忽略，保留上一次状态 */
      }
    }, 2000);
    return () => clearInterval(timer);
  }, [info, orderEvent?.status]);

  // 用相同 payload + 幂等键再次提交，验证幂等回放（后端命中 HIT 返回 200，不新建订单）
  const handleReplay = async () => {
    if (!info?.idempotencyKey) return;
    setReplaying(true);
    setReplayError(null);
    try {
      const result = await api.createOrder(info.payload, info.idempotencyKey);
      setInfo({
        order: result.order,
        promotion: result.promotion,
        idempotencyKey: info.idempotencyKey,
        replayed: result.httpStatus === 200,
        payload: info.payload,
      });
      loadOrders();
    } catch (err: unknown) {
      const e = err as { message?: string };
      setReplayError(`重放失败：${e.message || '未知错误'}`);
    } finally {
      setReplaying(false);
    }
  };

  return (
    <div className="order-page">
      <OrderForm onOrderCreated={handleOrderCreated} />

      {info && (
        <div className="card order-result">
          <h3 className={info.replayed ? 'replayed' : ''}>
            {info.replayed ? '幂等命中：回放首次响应' : '订单创建成功'}
          </h3>
          {info.idempotencyKey && (
            <p className="idem-tag">
              <span className="label">幂等键：</span>
              <code>{info.idempotencyKey}</code>
            </p>
          )}
          <div className="order-detail">
            <p><span className="label">订单 ID：</span>{info.order.id}</p>
            <p><span className="label">用户 ID：</span>{info.order.userId}</p>
            <p><span className="label">状态：</span>{info.order.status}</p>
          </div>

          <div className="outbox-event">
            <div className="outbox-event-head">
              <span className="outbox-event-title">事件驱动（事务 Outbox）</span>
              <button className="btn btn-ghost btn-sm" onClick={() => loadOrderEvent(info.order.id)}>
                刷新事件状态
              </button>
            </div>
            <p className="outbox-event-desc">
              <span className="label">OrderCreated 事件：</span>
              {orderEvent ? (
                orderEvent.status === 'PUBLISHED' ? (
                  <span className="evt-status evt-published">已发布到 Kafka（PUBLISHED）</span>
                ) : (
                  <span className="evt-status evt-pending">已入 Outbox，待中继发布（PENDING）</span>
                )
              ) : (
                <span className="evt-status evt-pending">已与订单同事务写入 Outbox，即将中继发布</span>
              )}
            </p>
            <Link to="/status" className="outbox-link">去运行态查看完整事件流 →</Link>
          </div>

          {info.idempotencyKey && (
            <button className="btn btn-ghost replay-btn" onClick={handleReplay} disabled={replaying}>
              {replaying ? '重放中...' : '用相同幂等键重放'}
            </button>
          )}
          {replayError && <p className="replay-error">{replayError}</p>}
        </div>
      )}

      {info && (
        <div className="card promotion-result">
          <h3>优惠信息</h3>
          <div className="promotion-detail">
            <p><span className="label">原始金额：</span>¥{info.promotion.originalAmount.toFixed(2)}</p>
            <p><span className="label">折扣金额：</span>¥{info.promotion.discount.toFixed(2)}</p>
            <p className="final-price"><span className="label">最终价格：</span>¥{info.promotion.finalPrice.toFixed(2)}</p>
            <p><span className="label">适用规则：</span>{ruleLabels[info.promotion.appliedRule] || info.promotion.appliedRule}</p>
          </div>
        </div>
      )}

      <div className="card order-list">
        <div className="order-list-head">
          <h3>订单列表</h3>
          <div className="order-list-actions">
            <button className="btn btn-ghost" onClick={loadOrders} disabled={loadingOrders}>
              {loadingOrders ? '刷新中...' : '刷新'}
            </button>
            {orders.length > 0 && (
              <button className="btn btn-danger" onClick={handleClear} disabled={clearing}>
                {clearing ? '清理中...' : '清理'}
              </button>
            )}
          </div>
        </div>
        {clearError && <p className="replay-error">{clearError}</p>}
        {orders.length === 0 ? (
          <p className="order-empty">暂无订单</p>
        ) : (
          <table className="order-table">
            <thead>
              <tr>
                <th>订单 ID</th>
                <th>用户</th>
                <th>金额</th>
                <th>优惠</th>
                <th>实付</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>{o.id}</td>
                  <td>
                    {userMap[o.userId] ? (
                      <span className="user-name">{userMap[o.userId].name}</span>
                    ) : (
                      o.userId
                    )}
                  </td>
                  <td>¥{Number(o.amount).toFixed(2)}</td>
                  <td>¥{Number(o.discount ?? 0).toFixed(2)}</td>
                  <td>¥{Number(o.finalAmount ?? o.amount).toFixed(2)}</td>
                  <td>{o.status}</td>
                  <td>{o.createdAt ? new Date(o.createdAt).toLocaleString() : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
