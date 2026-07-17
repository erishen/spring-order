import { useState } from 'react';
import { api } from '../api/client';
import type { PromotionResult, OrderResponse } from '../types';

interface OrderFormProps {
  onOrderCreated: (order: OrderResponse, promotion: PromotionResult) => void;
}

export function OrderForm({ onOrderCreated }: OrderFormProps) {
  const [userId, setUserId] = useState('');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

    setLoading(true);
    try {
      const result = await api.createOrder({ userId: userId.trim(), amount: numAmount });
      onOrderCreated(result.order, result.promotion);
      setUserId('');
      setAmount('');
    } catch (err: unknown) {
      const errObj = err as { status?: number; message?: string; errors?: string[] };
      if (errObj.status === 400) {
        setError(errObj.message || errObj.errors?.join(', ') || '请求参数错误');
      } else {
        setError(errObj.message || '创建订单失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form className="order-form" onSubmit={handleSubmit}>
      <h2>创建订单</h2>

      <div className="form-group">
        <label htmlFor="userId">用户 ID</label>
        <input
          id="userId"
          type="text"
          value={userId}
          onChange={(e) => setUserId(e.target.value)}
          placeholder="请输入用户 ID"
          disabled={loading}
        />
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

      {error && <div className="form-error">{error}</div>}

      <button type="submit" className="btn btn-primary" disabled={loading}>
        {loading ? '提交中...' : '创建订单'}
      </button>
    </form>
  );
}
