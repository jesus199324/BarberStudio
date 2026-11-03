-- V5: fondo de portada y modo de la imagen delantera (foto o logo)
ALTER TABLE barbershop
  ADD COLUMN hero_bg_url   VARCHAR(500),
  ADD COLUMN hero_bg_key   VARCHAR(255),
  ADD COLUMN hero_art_mode VARCHAR(10) NOT NULL DEFAULT 'photo' CHECK (hero_art_mode IN ('photo', 'logo'));
