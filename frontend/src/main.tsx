import { App } from './App';

async function bootstrap() {
  // MSW mock 默认关闭：`make dev` 会同时起后端，前端应直连真实后端（经 vite proxy），
  // 订单才会真实持久化到 H2 文件库、幂等/Outbox 才真实工作。
  // 纯前端演示（不起后端）时才用 `VITE_ENABLE_MOCK=true` 显式开启 mock。
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK === 'true') {
    const { worker } = await import('./mocks/browser');
    await worker.start({ onUnhandledRequest: 'bypass' });
  }

  const { createRoot } = await import('react-dom/client');
  const root = document.getElementById('root');
  if (!root) throw new Error('Root element not found');
  createRoot(root).render(<App />);
}

bootstrap();
