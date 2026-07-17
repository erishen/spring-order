import { App } from './App';

async function bootstrap() {
  if (import.meta.env.DEV) {
    const { worker } = await import('./mocks/browser');
    await worker.start({ onUnhandledRequest: 'bypass' });
  }

  const { createRoot } = await import('react-dom/client');
  const root = document.getElementById('root');
  if (!root) throw new Error('Root element not found');
  createRoot(root).render(<App />);
}

bootstrap();
