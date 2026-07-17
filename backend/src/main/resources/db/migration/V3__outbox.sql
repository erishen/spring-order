-- P3: transactional Outbox table.
-- Events are written in the same DB transaction as the business row (Order),
-- then relayed to Kafka by OutboxRelay. Survives both H2 and Postgres.
CREATE TABLE outbox_events (
    id             VARCHAR(36)  PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    published_at   TIMESTAMP,
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status ON outbox_events (status);
