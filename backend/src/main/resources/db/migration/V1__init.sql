-- P0: schema for the order platform (compatible with H2 and PostgreSQL).
-- Schema ownership is delegated to Flyway; Hibernate runs with ddl-auto=none.

CREATE TABLE orders (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(255) NOT NULL,
    amount       DECIMAL(19,2) NOT NULL,
    discount     DECIMAL(19,2),
    final_amount DECIMAL(19,2),
    status       VARCHAR(50),
    created_at   TIMESTAMP,
    version      BIGINT
);

CREATE TABLE inventory (
    stock_id  VARCHAR(255) PRIMARY KEY,
    available INT          NOT NULL,
    version   BIGINT
);

-- Seed a default shared stock pool. The @Version column starts at 0; Hibernate
-- manages it on every update (this is what prevents oversell under concurrency).
INSERT INTO inventory (stock_id, available, version) VALUES ('DEFAULT', 1000, 0);
