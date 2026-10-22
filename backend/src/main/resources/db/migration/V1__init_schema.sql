-- Barber Studio - esquema inicial
-- Fechas en timestamptz (UTC). La conversion a America/Montevideo se hace en la aplicacion.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE barbershop (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    slug           VARCHAR(80)  NOT NULL UNIQUE,
    about_text     TEXT,
    whatsapp_e164  VARCHAR(16)  NOT NULL CHECK (whatsapp_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    address        VARCHAR(255),
    timezone       VARCHAR(60)  NOT NULL DEFAULT 'America/Montevideo',
    currency       CHAR(3)      NOT NULL DEFAULT 'UYU',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id  BIGINT       NOT NULL REFERENCES barbershop(id),
    email          VARCHAR(160) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'BARBER')),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));

CREATE TABLE client (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id  BIGINT       NOT NULL REFERENCES barbershop(id),
    name           VARCHAR(120) NOT NULL,
    whatsapp_e164  VARCHAR(16)  NOT NULL CHECK (whatsapp_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    preferences    TEXT,
    notes          TEXT,
    archived_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Un telefono por cliente ACTIVO; al archivar, el numero queda libre.
CREATE UNIQUE INDEX uq_client_active_phone
    ON client (barbershop_id, whatsapp_e164) WHERE archived_at IS NULL;
CREATE INDEX idx_client_name ON client (barbershop_id, lower(name));

CREATE TABLE service (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id    BIGINT        NOT NULL REFERENCES barbershop(id),
    name             VARCHAR(120)  NOT NULL,
    description      TEXT,
    price            NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    duration_minutes INTEGER       NOT NULL CHECK (duration_minutes > 0),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_shop ON service (barbershop_id, active);

-- Cubre "categorias" y "estilos": una sola tabla, sin jerarquia.
CREATE TABLE haircut_style (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id  BIGINT       NOT NULL REFERENCES barbershop(id),
    name           VARCHAR(80)  NOT NULL,
    slug           VARCHAR(80)  NOT NULL,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (barbershop_id, slug)
);

CREATE TABLE portfolio_item (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id  BIGINT       NOT NULL REFERENCES barbershop(id),
    style_id       BIGINT       NOT NULL REFERENCES haircut_style(id),
    title          VARCHAR(140) NOT NULL,
    description    TEXT,
    tags           TEXT[]       NOT NULL DEFAULT '{}',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX idx_portfolio_status ON portfolio_item (barbershop_id, status, published_at DESC);
CREATE INDEX idx_portfolio_style ON portfolio_item (style_id);
CREATE INDEX idx_portfolio_tags ON portfolio_item USING GIN (tags);

CREATE TABLE portfolio_image (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id       BIGINT       NOT NULL REFERENCES portfolio_item(id) ON DELETE CASCADE,
    storage_key   VARCHAR(255) NOT NULL,
    url           VARCHAR(500) NOT NULL,
    alt_text      VARCHAR(255) NOT NULL,
    position      INTEGER      NOT NULL DEFAULT 0,
    is_cover      BOOLEAN      NOT NULL DEFAULT FALSE,
    content_type  VARCHAR(60),
    size_bytes    BIGINT CHECK (size_bytes IS NULL OR size_bytes > 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_portfolio_image_item ON portfolio_image (item_id, position);
CREATE UNIQUE INDEX uq_portfolio_one_cover ON portfolio_image (item_id) WHERE is_cover;

CREATE TABLE appointment (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id    BIGINT        NOT NULL REFERENCES barbershop(id),
    client_id        BIGINT        NOT NULL REFERENCES client(id),
    service_id       BIGINT        NOT NULL REFERENCES service(id),
    start_at         TIMESTAMPTZ   NOT NULL,
    end_at           TIMESTAMPTZ   NOT NULL,
    -- Instantanea del catalogo al momento de reservar:
    price_agreed     NUMERIC(10,2) NOT NULL CHECK (price_agreed >= 0),
    duration_minutes INTEGER       NOT NULL CHECK (duration_minutes > 0),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    notes            TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_at > start_at),
    -- La base impide solapamientos aun con concurrencia (v1: un barbero por barberia).
    -- Intervalo semiabierto [inicio, fin): 10:00-10:30 y 10:30-11:00 NO chocan.
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        barbershop_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX idx_appointment_agenda ON appointment (barbershop_id, start_at);
CREATE INDEX idx_appointment_client ON appointment (client_id, start_at DESC);

CREATE TABLE visit (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id     BIGINT        NOT NULL REFERENCES barbershop(id),
    appointment_id    BIGINT        NOT NULL UNIQUE REFERENCES appointment(id), -- evita visitas/ingresos duplicados
    client_id         BIGINT        NOT NULL REFERENCES client(id),
    performed_at      TIMESTAMPTZ   NOT NULL,
    service_name      VARCHAR(120)  NOT NULL,   -- instantanea historica
    final_price       NUMERIC(10,2) NOT NULL CHECK (final_price >= 0),
    style_id          BIGINT REFERENCES haircut_style(id),
    portfolio_item_id BIGINT REFERENCES portfolio_item(id),
    notes             TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_visit_client ON visit (client_id, performed_at DESC);
CREATE INDEX idx_visit_income ON visit (barbershop_id, performed_at);

CREATE TABLE appointment_request (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id       BIGINT       NOT NULL REFERENCES barbershop(id),
    name                VARCHAR(120) NOT NULL,
    whatsapp_e164       VARCHAR(16)  NOT NULL CHECK (whatsapp_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    style_id            BIGINT REFERENCES haircut_style(id),
    portfolio_item_id   BIGINT REFERENCES portfolio_item(id),
    message             TEXT,
    preferred_time_text VARCHAR(160),
    status              VARCHAR(20)  NOT NULL DEFAULT 'NEW'
                        CHECK (status IN ('NEW', 'ACCEPTED', 'REJECTED')),
    appointment_id      BIGINT UNIQUE REFERENCES appointment(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((status = 'ACCEPTED') = (appointment_id IS NOT NULL))
);
CREATE INDEX idx_request_status ON appointment_request (barbershop_id, status, created_at DESC);
