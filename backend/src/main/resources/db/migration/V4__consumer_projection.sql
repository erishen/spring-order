-- P0: durable read-model projection for the OrderEventLogger consumer.
-- Compatible with H2 and PostgreSQL (no AUTO_INCREMENT / serial syntax).
-- order_id is the natural key; the consumer de-duplicates by it before insert.

CREATE TABLE consumed_orders (
    order_id     VARCHAR(255) PRIMARY KEY,
    user_id      VARCHAR(255),
    amount       DECIMAL(19,2),
    discount     DECIMAL(19,2),
    final_amount DECIMAL(19,2),
    status       VARCHAR(50),
    consumed_at  TIMESTAMP
);
