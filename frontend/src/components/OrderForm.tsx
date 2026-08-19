import { useState, useEffect } from 'react';
import { api } from '../api/client';
import type { PromotionResult, OrderResponse, User } from '../types';

export interface OrderCreatedInfo {
  order: OrderResponse;
  promotion: PromotionResult;
  idempotencyKey?: string;
  replayed: boolean;
  payload: { userId: string; amount: number };
}

interface OrderFormProps {
  onOrderCreated: (info: OrderCreatedInfo) => void;
}

const STATUS_HINTS: Record<number, string> = {
  409: '并发冲突：乐观锁重试仍冲突，或幂等键正在进行中',
  429: '限流触发：超过默认 10 次/秒，请稍后重试',
  503: '熔断器开闸：失败率过高，请稍后重试',
};

export function OrderForm({ onOrderCreated }: OrderFormProps) {
  const [userId, setUserId] = useState('');
  const [amount, setAmount] = useState('');
  const [idemKey, setIdemKey] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.getUsers().then(setUsers).catch(() => setUsers([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!userId.trim()) {
      setError('用户 ID 不能为空');
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setError('金额必须为正数');
      return;
    }

    const payload = { userId: userId.trim(), amount: numAmount };
    const key = idemKey.trim() || undefined;

    setLoading(true);
    try {
      const result = await api.createOrder(payload, key);
      onOrderCreated({
        order: result.order,
        promotion: result.promotion,
        idempotencyKey: key,
        replayed: result.httpStatus === 200,
        payload,
      });
      setUserId('');
      setAmount('');
      setIdemKey('');
    } catch (err: unknown) {
      const errObj = err as { status?: number; message?: string; errors?: string[] };
      const hint = STATUS_HINTS[errObj.status ?? 0];
      // 把 HTTP 状态码带出来，方便区分是后端错误(5xx)还是网关/网络不可达(502/0)
      const statusTag = typeof errObj.status === 'number' && errObj.status > 0 ? `（HTTP ${errObj.status}）` : '';
      const detail = errObj.message || errObj.errors?.join(', ') || '创建订单失败，请稍后重试';
      setError(hint ? `${hint}${errObj.message ? '：' + errObj.message : ''}` : `${detail}${statusTag}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form className="order-form" onSubmit={handleSubmit}>
      <h2>创建订单</h2>

      <div className="form-group">
        <label htmlFor="userId">用户</label>
        <select
          id="userId"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          disabled={loading}
        >
          <option value="">请选择用户</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}（{u.email}）
            </option>
          ))}
        </select>
      </div>

      <div className="form-group">
        <label htmlFor="amount">金额</label>
        <input
          id="amount"
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="请输入金额"
          min="0.01"
          step="0.01"
          disabled={loading}
        />
      </div>

      <div className="form-group">
        <label htmlFor="idemKey">
          幂等键（可选）
        </label>
        <input
          id="idemKey"
          type="text"
          value={idemKey}
          onChange={(e) => setIdemKey(e.target.value)}
          placeholder="如 order-2026-001，留空则每次新建订单"
          disabled={loading}
        />
        <p className="field-hint">填相同键重复提交会回放首次结果，不会重复下单（验证幂等）</p>
      </div>

      {error && <div className="form-error">{error}</div>}

      <button type="submit" className="btn btn-primary" disabled={loading}>
        {loading ? '提交中...' : '创建订单'}
      </button>
    </form>
  );
}
