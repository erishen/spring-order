import { useState, useEffect } from 'react';
import { OrderForm } from '../components/OrderForm';
import { api } from '../api/client';
import type { OrderResponse, PromotionResult } from '../types';

export function OrderPage() {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [promotion, setPromotion] = useState<PromotionResult | null>(null);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [loadingOrders, setLoadingOrders] = useState(false);

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

  useEffect(() => {
    loadOrders();
  }, []);

  const handleOrderCreated = (ord: OrderResponse, promo: PromotionResult) => {
    setOrder(ord);
    setPromotion(promo);
    // Refresh the list so the new order shows up (also exercises the Redis
    // @Cacheable("orders-all") path on the backend).
    loadOrders();
  };

  return (
    <div className="order-page">
      <OrderForm onOrderCreated={handleOrderCreated} />

      {order && (
        <div className="card order-result">
          <h3>订单创建成功</h3>
          <div className="order-detail">
            <p><span className="label">订单 ID：</span>{order.id}</p>
            <p><span className="label">用户 ID：</span>{order.userId}</p>
            <p><span className="label">状态：</span>{order.status}</p>
          </div>
        </div>
      )}

      {promotion && (
        <div className="card promotion-result">
          <h3>优惠信息</h3>
          <div className="promotion-detail">
            <p><span className="label">原始金额：</span>¥{promotion.originalAmount.toFixed(2)}</p>
            <p><span className="label">折扣金额：</span>¥{promotion.discount.toFixed(2)}</p>
            <p className="final-price"><span className="label">最终价格：</span>¥{promotion.finalPrice.toFixed(2)}</p>
            <p><span className="label">适用规则：</span>{ruleLabels[promotion.appliedRule] || promotion.appliedRule}</p>
          </div>
        </div>
      )}

      <div className="card order-list">
        <div className="order-list-head">
          <h3>订单列表</h3>
          <button className="btn btn-ghost" onClick={loadOrders} disabled={loadingOrders}>
            {loadingOrders ? '刷新中...' : '刷新'}
          </button>
        </div>
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
                  <td>{o.userId}</td>
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
