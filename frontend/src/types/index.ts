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
  status: string;
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
