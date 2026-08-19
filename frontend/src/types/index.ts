export interface OrderCreateRequest {
  userId: string;
  amount: number;
  stockId?: string;
  requiredStock?: number;
}

export interface OrderResponse {
  id: string;
  userId: string;
  amount: number;
  discount: number;
  finalAmount: number;
  status: string;
  createdAt: string;
}

export type OutboxStatusView = 'PENDING' | 'PUBLISHED';

export interface EventView {
  id: string;
  eventType: string;
  aggregateId: string;
  status: OutboxStatusView;
  createdAt: string;
  publishedAt: string | null;
}

export interface KafkaStatus {
  configured: boolean;
  connected: boolean;
  bootstrapServers: string;
  internalBootstrapServers: string;
}

export interface ConsumedOrder {
  orderId: string;
  userId: string;
  amount: string | null;
  discount: string | null;
  finalAmount: string | null;
  status: string | null;
  consumedAt: string | null;
}

export interface ConsumerProjection {
  totalConsumed: number;
  lastConsumedAt: string | null;
  recentOrders: ConsumedOrder[];
  consumedOrderIds: string[];
}

export interface InstanceInfo {
  app?: string;
  instanceId?: string;
  [key: string]: unknown;
}

export interface User {
  id: string;
  name: string;
  email: string;
}

export interface PromotionResult {
  originalAmount: number;
  discount: number;
  finalPrice: number;
  appliedRule: string;
}

export interface OrderWithPromotion {
  order: OrderResponse;
  promotion: PromotionResult;
}

export interface CreateOrderResult extends OrderWithPromotion {
  httpStatus: number;
}
