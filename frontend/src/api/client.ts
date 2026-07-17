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

  getUsers: () =>
    request<import('../types').User[]>('/api/users'),
};
