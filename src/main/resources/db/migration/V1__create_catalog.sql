-- A table with enough rows for a query to be a real query, and an index so the
-- lookup is cheap. The blocking in this benchmark comes from pg_sleep, not from
-- a badly written query: a controlled delay is what makes two thread models
-- comparable.

CREATE TABLE product (
    id       BIGSERIAL     PRIMARY KEY,
    sku      VARCHAR(20)   NOT NULL UNIQUE,
    name     VARCHAR(120)  NOT NULL,
    price    NUMERIC(12,2) NOT NULL,
    in_stock INTEGER       NOT NULL
);

CREATE INDEX idx_product_sku ON product (sku);

INSERT INTO product (sku, name, price, in_stock)
SELECT
    'SKU-' || lpad(n::text, 8, '0'),
    'Product ' || n,
    (random() * 500 + 10)::numeric(12,2),
    (random() * 200)::int
FROM generate_series(1, 20000) AS n;
