-- V7: clave de almacenamiento de la foto del barbero (para poder reemplazarla o borrarla)
ALTER TABLE barber ADD COLUMN photo_key VARCHAR(255);
