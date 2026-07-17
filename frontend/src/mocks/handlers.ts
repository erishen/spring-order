import { http, HttpResponse } from 'msw';

// Mirrors the backend: a shared stock pool (seeded DEFAULT=1000) the order
// draws from. Available stock is server-side state, not sent by the client.
const inventory: Record<string, number> = { DEFAULT: 1000 };

const mockUsers = [
  { id: 'u1', name: 'Alice Johnson', email: 'alice@example.com' },
  { id: 'u2', name: 'Bob Smith', email: 'bob@example.com' },
  { id: 'u3', name: 'Carol Williams', email: 'carol@example.com' },
];

let orderIdCounter = 1;

// Track which userIds have ordered before, so "new user" is derived from
// history exactly like the backend OrderService.
const seenUsers = new Set<string>();

export const handlers = [
  http.post('/api/orders', async ({ request }) => {
    const body = (await request.json()) as {
      userId?: string;
      amount?: number;
      stockId?: string;
      requiredStock?: number;
    };

    if (!body.userId || body.userId.trim() === '') {
      return HttpResponse.json({ message: '用户 ID 不能为空' }, { status: 400 });
    }

    if (!body.amount || body.amount <= 0) {
      return HttpResponse.json({ message: '金额必须为正数' }, { status: 400 });
    }

    const stockId = body.stockId || 'DEFAULT';
    const requiredStock = body.requiredStock || 1;
    const available = inventory[stockId] ?? 0;

    // Rule 4: insufficient stock -> 400 (mirrors backend InsufficientStockException).
    if (requiredStock > available) {
      return HttpResponse.json(
        { error: 'Insufficient Stock', message: `required=${requiredStock}, available=${available}` },
        { status: 400 },
      );
    }
    inventory[stockId] = available - requiredStock;

    const userId = body.userId.trim();
    const isNewUser = !seenUsers.has(userId);
    seenUsers.add(userId);

    // Promotion rules aligned with the backend PromotionService:
    //   Rule 2: amount >= 100 -> -20 (full reduction)
    //   Rule 1: new user      -> -10
    //   Rule 3: not stackable, full reduction takes priority
    let discount = 0;
    let appliedRule = 'none';
    if (body.amount >= 100) {
      discount = 20;
      appliedRule = 'full_reduction';
    } else if (isNewUser) {
      discount = 10;
      appliedRule = 'new_user';
    }

    const finalPrice = Math.max(0, body.amount - discount);
    const orderId = `ORD-${String(orderIdCounter++).padStart(4, '0')}`;

    return HttpResponse.json({
      order: {
        id: orderId,
        userId,
        amount: body.amount,
        status: 'CREATED',
      },
      promotion: {
        originalAmount: body.amount,
        discount,
        finalPrice,
        appliedRule,
      },
    });
  }),

  http.get('/api/users', () => {
    return HttpResponse.json(mockUsers);
  }),
];
