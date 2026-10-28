-- V3: imagen de portada opcional del sitio (logo o foto del barbero)
ALTER TABLE barbershop
  ADD COLUMN logo_url VARCHAR(500),
  ADD COLUMN logo_key VARCHAR(255);
