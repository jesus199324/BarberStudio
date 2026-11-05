-- V6: seccion "Acerca de" editable desde el panel (titulo y texto; los parrafos se separan con una linea en blanco)
ALTER TABLE barbershop
  ADD COLUMN about_title VARCHAR(120),
  ADD COLUMN about_body  TEXT;
