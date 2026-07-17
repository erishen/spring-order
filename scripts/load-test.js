// k6 load test for the P4 multi-instance demo.
// Drives the nginx gateway (:80) with concurrent order creations; each request
// carries a unique Idempotency-Key (P2) so retries are safe. Watch per-replica
// metrics in Prometheus/Grafana to see the gateway spreading load across replicas.
//
// Run:  k6 run -e BASE=http://localhost scripts/load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost';

export const options = {
  vus: 20,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

function rid(n) {
  const c = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < n; i++) s += c[Math.floor(Math.random() * c.length)];
  return s;
}

export default function () {
  const key = `k6-${rid(12)}`;
  const res = http.post(
    `${BASE}/api/orders`,
    JSON.stringify({ userId: `u-${rid(6)}`, amount: 120, stockId: 'DEFAULT', requiredStock: 1 }),
    { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } }
  );
  check(res, { 'created or rejected (200/400/429)': (r) => [200, 400, 429].includes(r.status) });
  sleep(0.5);
}
