"""Locust load test for the P4 multi-instance demo.

Drives the nginx gateway (:80) with concurrent order creations. Each request
carries a unique Idempotency-Key (P2) so retries are safe. Watch per-replica
metrics in Prometheus/Grafana to see the gateway spreading load across replicas.

Run:  locust -f scripts/load-test.py --headless -u 50 -r 10 -t 1m -H http://localhost
"""
import random
import string

from locust import HttpUser, between, task

BASE_PATH = "/api/orders"


def _rand(n: int) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


class OrderUser(HttpUser):
    wait_time = between(0.5, 1.5)

    @task
    def create_order(self):
        key = f"locust-{_rand(12)}"
        self.client.post(
            BASE_PATH,
            json={"userId": f"u-{_rand(6)}", "amount": 120, "stockId": "DEFAULT", "requiredStock": 1},
            headers={"Content-Type": "application/json", "Idempotency-Key": key},
            name="POST /api/orders",
        )
