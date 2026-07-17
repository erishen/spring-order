import { useState } from 'react';
import { OrderForm } from '../components/OrderForm';
import type { OrderResponse, PromotionResult } from '../types';

export function OrderPage() {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [promotion, setPromotion] = useState<PromotionResult | null>(null);

  const handleOrderCreated = (ord: OrderResponse, promo: PromotionResult) => {
    setOrder(ord);
    setPromotion(promo);
  };

  const ruleLabels: Record<string, string> = {
    new_user: '新用户优惠',
    full_reduction: '满减优惠',
    none: '无优惠',
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
    </div>
  );
}
