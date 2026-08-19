const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({ message: res.statusText }));
    throw { status: res.status, ...body };
  }

  return res.json();
}

export const api = {
  createOrder: async (
    data: import('../types').OrderCreateRequest,
    idempotencyKey?: string,
  ): Promise<import('../types').CreateOrderResult> => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    const res = await fetch(`${API_BASE_URL}/api/orders`, {
      method: 'POST',
      body: JSON.stringify(data),
      headers,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw { status: res.status, ...body };
    return { ...body, httpStatus: res.status };
  },

  getOrders: () =>
    request<import('../types').OrderResponse[]>('/api/orders'),

  deleteOrders: () =>
    request<{ removed: number; message: string }>('/api/orders', { method: 'DELETE' }),

  getEvents: () =>
    request<import('../types').EventView[]>('/api/events'),

  getKafkaStatus: () =>
    request<import('../types').KafkaStatus>('/api/kafka/status'),

  getConsumerProjection: () =>
    request<import('../types').ConsumerProjection>('/api/consumer/projection'),

  getUsers: () =>
    request<import('../types').User[]>('/api/users'),

  // Raw actuator endpoints (no JSON envelope) — proxied by vite/dev and the
  // frontend nginx in prod. Used by the runtime-status page.
  getInstanceInfo: () => request<import('../types').InstanceInfo>('/actuator/info'),

  getPrometheus: async () => {
    const res = await fetch(`${API_BASE_URL}/actuator/prometheus`, { headers: { Accept: 'text/plain' } });
    if (!res.ok) throw { status: res.status, message: res.statusText };
    return res.text();
  },
};
