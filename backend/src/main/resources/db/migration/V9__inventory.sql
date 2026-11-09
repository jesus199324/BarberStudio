-- Inventario: productos de cuidado y el historial de cada cambio de stock.
CREATE TABLE product (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id       BIGINT        NOT NULL REFERENCES barbershop(id),
    name                VARCHAR(120)  NOT NULL,
    description         TEXT,
    price               NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock               INTEGER       NOT NULL DEFAULT 0 CHECK (stock >= 0),
    low_stock_threshold INTEGER       NOT NULL DEFAULT 2 CHECK (low_stock_threshold >= 0),
    image_url           VARCHAR(500),
    image_key           VARCHAR(300),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    visible_in_shop     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_product_shop_name ON product (barbershop_id, lower(trim(name)));

CREATE TABLE stock_movement (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id BIGINT       NOT NULL REFERENCES barbershop(id),
    product_id    BIGINT       NOT NULL REFERENCES product(id),
    delta         INTEGER      NOT NULL CHECK (delta <> 0),
    reason        VARCHAR(20)  NOT NULL CHECK (reason IN ('SALE','ORDER','PURCHASE','ADJUSTMENT','VOID','ORDER_CANCEL')),
    sale_id       BIGINT,      -- la FK se agrega en V10 (tabla sale)
    order_id      BIGINT,      -- la FK se agrega en V11 (tabla shop_order)
    user_id       BIGINT       REFERENCES app_user(id),
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_movement_product ON stock_movement (product_id, created_at DESC);
