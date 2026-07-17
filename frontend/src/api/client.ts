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
  createOrder: (data: import('../types').OrderCreateRequest) =>
    request<{ order: import('../types').OrderResponse; promotion: import('../types').PromotionResult }>('/api/orders', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getOrders: () =>
    request<import('../types').OrderResponse[]>('/api/orders'),

  getEvents: () =>
    request<import('../types').EventView[]>('/api/events'),

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
