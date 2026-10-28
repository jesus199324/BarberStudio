-- V4: datos del negocio para el footer y trabajos destacados del portfolio
ALTER TABLE barbershop
  ADD COLUMN instagram_url VARCHAR(300),
  ADD COLUMN tiktok_url    VARCHAR(300),
  ADD COLUMN youtube_url   VARCHAR(300),
  ADD COLUMN neighborhood  VARCHAR(120),
  ADD COLUMN city          VARCHAR(120),
  ADD COLUMN maps_url      VARCHAR(500),
  ADD COLUMN hours_note    VARCHAR(300);

ALTER TABLE portfolio_item
  ADD COLUMN featured   BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
