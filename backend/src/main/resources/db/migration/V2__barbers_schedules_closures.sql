-- V2: barberos, horarios, cierres, ajustes de reserva, categorias de estilos, datos de cliente

ALTER TABLE barbershop
  ADD COLUMN slot_minutes        INTEGER NOT NULL DEFAULT 15 CHECK (slot_minutes BETWEEN 5 AND 120),
  ADD COLUMN buffer_minutes      INTEGER NOT NULL DEFAULT 0  CHECK (buffer_minutes BETWEEN 0 AND 120),
  ADD COLUMN auto_confirm        BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN max_days_ahead      INTEGER NOT NULL DEFAULT 60 CHECK (max_days_ahead BETWEEN 1 AND 365),
  ADD COLUMN min_notice_minutes  INTEGER NOT NULL DEFAULT 60 CHECK (min_notice_minutes >= 0);

ALTER TABLE haircut_style
  ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'CORTE' CHECK (category IN ('CORTE','COLOR','BARBA','TATUAJE'));

ALTER TABLE client
  ADD COLUMN birth_date DATE,
  ADD COLUMN marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN marketing_opt_in_at TIMESTAMPTZ;

CREATE TABLE barber (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id BIGINT       NOT NULL REFERENCES barbershop(id),
    name          VARCHAR(120) NOT NULL,
    bio           TEXT,
    photo_url     VARCHAR(500),
    specialties   VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_barber_shop ON barber (barbershop_id, active);

CREATE TABLE barber_service (
    barber_id  BIGINT NOT NULL REFERENCES barber(id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES service(id),
    PRIMARY KEY (barber_id, service_id)
);

-- Horario semanal habitual (weekday: 1 = lunes ... 7 = domingo), con un descanso opcional.
CREATE TABLE working_hours (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barber_id   BIGINT   NOT NULL REFERENCES barber(id) ON DELETE CASCADE,
    weekday     SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    break_start TIME,
    break_end   TIME,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    CHECK ((break_start IS NULL) = (break_end IS NULL)),
    CHECK (break_start IS NULL OR (break_end > break_start AND break_start >= start_time AND break_end <= end_time)),
    UNIQUE (barber_id, weekday)
);

-- Excepcion para UNA fecha: dia libre o horario distinto, sin tocar las demas semanas.
CREATE TABLE schedule_override (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barber_id   BIGINT  NOT NULL REFERENCES barber(id) ON DELETE CASCADE,
    work_date   DATE    NOT NULL,
    day_off     BOOLEAN NOT NULL DEFAULT FALSE,
    start_time  TIME,
    end_time    TIME,
    break_start TIME,
    break_end   TIME,
    note        VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (day_off OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)),
    CHECK ((break_start IS NULL) = (break_end IS NULL)),
    UNIQUE (barber_id, work_date)
);

-- Cierres y licencias. barber_id NULL = cierre general del negocio. Sin horas = dia completo.
CREATE TABLE closure (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id BIGINT NOT NULL REFERENCES barbershop(id),
    barber_id     BIGINT REFERENCES barber(id) ON DELETE CASCADE,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    start_time    TIME,
    end_time      TIME,
    reason        VARCHAR(30) NOT NULL CHECK (reason IN
                  ('FERIADO','VACACIONES','CIERRE_EXTRAORDINARIO','MANTENIMIENTO','PERSONAL','LICENCIA','OTRO')),
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date),
    CHECK ((start_time IS NULL) = (end_time IS NULL)),
    CHECK (start_time IS NULL OR end_time > start_time)
);
CREATE INDEX idx_closure_range ON closure (barbershop_id, start_date, end_date);

-- Cada turno pertenece a un barbero; los turnos existentes pasan al "Barbero principal".
ALTER TABLE appointment ADD COLUMN barber_id BIGINT REFERENCES barber(id);
INSERT INTO barber (barbershop_id, name) SELECT id, 'Barbero principal' FROM barbershop;
UPDATE appointment a SET barber_id =
    (SELECT b.id FROM barber b WHERE b.barbershop_id = a.barbershop_id ORDER BY b.id LIMIT 1);
ALTER TABLE appointment ALTER COLUMN barber_id SET NOT NULL;
CREATE INDEX idx_appointment_barber ON appointment (barber_id, start_at);

-- El anti-solapamiento pasa a ser POR BARBERO: dos barberos pueden atender a la misma hora.
ALTER TABLE appointment DROP CONSTRAINT appointment_no_overlap;
ALTER TABLE appointment ADD CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
    barber_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
) WHERE (status <> 'CANCELLED');

-- Borrar una publicacion del portfolio no debe romper el historial.
ALTER TABLE visit DROP CONSTRAINT visit_portfolio_item_id_fkey;
ALTER TABLE visit ADD CONSTRAINT visit_portfolio_item_id_fkey
    FOREIGN KEY (portfolio_item_id) REFERENCES portfolio_item(id) ON DELETE SET NULL;
ALTER TABLE appointment_request DROP CONSTRAINT appointment_request_portfolio_item_id_fkey;
ALTER TABLE appointment_request ADD CONSTRAINT appointment_request_portfolio_item_id_fkey
    FOREIGN KEY (portfolio_item_id) REFERENCES portfolio_item(id) ON DELETE SET NULL;
