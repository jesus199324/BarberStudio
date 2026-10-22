-- DATOS DE DEMOSTRACION (solo perfil dev). Todo es ficticio.
-- Telefonos inventados +598 99 1xx xxx. El usuario demo lo crea DemoUserInitializer.

INSERT INTO barbershop (name, slug, about_text, whatsapp_e164, address)
VALUES ('Barber Studio (demo)', 'barber-studio-demo',
        'Contenido de demostración. Reemplazar por la presentación real de la barbería.',
        '+59899000000', 'Dirección de ejemplo, Montevideo');

INSERT INTO service (barbershop_id, name, description, price, duration_minutes)
SELECT b.id, v.name, v.description, v.price, v.duration
FROM barbershop b,
     (VALUES ('Corte clásico',   'Corte a tijera y máquina',            600, 30),
             ('Taper fade',      'Degradé con transición suave',         700, 45),
             ('Arreglo de barba','Perfilado y recorte de barba',         400, 20),
             ('Corte + barba',   'Combo de corte y arreglo de barba',    950, 60),
             ('Corte infantil',  'Corte para menores de 12 años',        500, 30))
       AS v(name, description, price, duration)
WHERE b.slug = 'barber-studio-demo';

INSERT INTO barber (barbershop_id, name, bio)
SELECT b.id, v.name, 'Barbero de demostración' FROM barbershop b, (VALUES ('Barbero Demo Uno'), ('Barbero Demo Dos')) AS v(name)
WHERE b.slug = 'barber-studio-demo';
INSERT INTO barber_service (barber_id, service_id) SELECT b.id, s.id FROM barber b CROSS JOIN service s;
-- Martes a sabado, 10:00 a 19:00 con descanso de 13:00 a 14:00 (solo demostracion).
INSERT INTO working_hours (barber_id, weekday, start_time, end_time, break_start, break_end)
SELECT b.id, d, '10:00', '19:00', '13:00', '14:00' FROM barber b, generate_series(2, 6) AS d;

INSERT INTO haircut_style (barbershop_id, name, slug, sort_order)
SELECT b.id, v.name, v.slug, v.ord
FROM barbershop b,
     (VALUES ('Taper fade','taper-fade',1), ('Low fade','low-fade',2), ('Mid fade','mid-fade',3),
             ('High fade','high-fade',4), ('Mohicano','mohicano',5), ('Corte clásico','corte-clasico',6),
             ('Crop','crop',7), ('Cortes con rulos','cortes-con-rulos',8),
             ('Cortes infantiles','cortes-infantiles',9), ('Otros','otros',10))
       AS v(name, slug, ord)
WHERE b.slug = 'barber-studio-demo';

INSERT INTO portfolio_item (barbershop_id, style_id, title, description, tags, status, published_at)
SELECT hs.barbershop_id, hs.id, v.title, v.description, v.tags::text[], v.status,
       CASE WHEN v.status = 'PUBLISHED' THEN now() - (v.days || ' days')::interval END
FROM haircut_style hs
JOIN (VALUES
  ('taper-fade',       'Taper fade clásico',   'Ejemplo de demostración.', '{taper,degradé}',  'PUBLISHED', 1),
  ('low-fade',         'Low fade con textura', 'Ejemplo de demostración.', '{low,textura}',    'PUBLISHED', 2),
  ('mid-fade',         'Mid fade prolijo',     'Ejemplo de demostración.', '{mid}',            'PUBLISHED', 3),
  ('high-fade',        'High fade marcado',    'Ejemplo de demostración.', '{high,contraste}', 'PUBLISHED', 4),
  ('mohicano',         'Mohicano moderno',     'Ejemplo de demostración.', '{mohicano}',       'PUBLISHED', 5),
  ('cortes-con-rulos', 'Rulos definidos',      'Ejemplo de demostración.', '{rulos}',          'PUBLISHED', 6),
  ('cortes-infantiles','Corte infantil',       'Ejemplo de demostración.', '{niños}',          'PUBLISHED', 7),
  ('crop',             'Crop (borrador)',      'Borrador: no debe verse en la galería pública.', '{crop}', 'DRAFT', 0)
) AS v(slug, title, description, tags, status, days) ON v.slug = hs.slug;

-- Una imagen de portada por publicacion (los archivos hay que agregarlos en static/demo-images/).
INSERT INTO portfolio_image (item_id, storage_key, url, alt_text, position, is_cover, content_type)
SELECT pi.id, 'demo/item-' || pi.id || '.jpg', '/demo-images/item-' || pi.id || '.jpg',
       'Foto de demostración: ' || pi.title, 0, TRUE, 'image/jpeg'
FROM portfolio_item pi;

INSERT INTO client (barbershop_id, name, whatsapp_e164, preferences, notes)
SELECT b.id, v.name, v.phone, v.prefs, v.notes
FROM barbershop b,
     (VALUES ('Cliente Demo Uno',   '+59899100001', 'Degradé bajo', 'Cliente ficticio'),
             ('Cliente Demo Dos',   '+59899100002', NULL,           'Cliente ficticio'),
             ('Cliente Demo Tres',  '+59899100003', 'Barba corta',  'Cliente ficticio'),
             ('Cliente Demo Cuatro','+59899100004', NULL,           'Cliente ficticio'),
             ('Cliente Demo Cinco', '+59899100005', 'Rulos',        'Cliente ficticio'))
       AS v(name, phone, prefs, notes)
WHERE b.slug = 'barber-studio-demo';

-- Turnos relativos a la fecha en que se ejecuta la migracion, en hora de Montevideo.
WITH d AS (SELECT date_trunc('day', now() AT TIME ZONE 'America/Montevideo') AS today)
INSERT INTO appointment (barbershop_id, client_id, service_id, barber_id, start_at, end_at,
                         price_agreed, duration_minutes, status, notes)
SELECT b.id, c.id, s.id, (SELECT min(id) FROM barber),
       (d.today + make_interval(days => t.day_offset, hours => t.hour)) AT TIME ZONE 'America/Montevideo',
       (d.today + make_interval(days => t.day_offset, hours => t.hour, mins => s.duration_minutes))
           AT TIME ZONE 'America/Montevideo',
       s.price, s.duration_minutes, t.status, t.notes
FROM d
CROSS JOIN barbershop b
CROSS JOIN (VALUES
  ('+59899100001', 'Taper fade',       -1, 15, 'COMPLETED', 'Turno de demostración'),
  ('+59899100002', 'Corte clásico',    -1, 16, 'CANCELLED', 'Cancelado por el cliente'),
  ('+59899100003', 'Corte + barba',     0, 10, 'CONFIRMED', NULL),
  ('+59899100004', 'Arreglo de barba',  0, 12, 'PENDING',   NULL),
  ('+59899100005', 'Taper fade',        1, 10, 'CONFIRMED', NULL)
) AS t(phone, service_name, day_offset, hour, status, notes)
JOIN client  c ON c.whatsapp_e164 = t.phone
JOIN service s ON s.name = t.service_name
WHERE b.slug = 'barber-studio-demo';

INSERT INTO visit (barbershop_id, appointment_id, client_id, performed_at, service_name,
                   final_price, style_id, notes)
SELECT a.barbershop_id, a.id, a.client_id, a.end_at, s.name, a.price_agreed, hs.id,
       'Visita de demostración'
FROM appointment a
JOIN service s ON s.id = a.service_id
JOIN haircut_style hs ON hs.barbershop_id = a.barbershop_id AND hs.slug = 'taper-fade'
WHERE a.status = 'COMPLETED';

INSERT INTO appointment_request (barbershop_id, name, whatsapp_e164, style_id, message, preferred_time_text)
SELECT b.id, 'Solicitante Demo', '+59899100099', hs.id,
       'Solicitud de demostración (no es un turno confirmado).', 'Sábado a la mañana'
FROM barbershop b JOIN haircut_style hs ON hs.barbershop_id = b.id AND hs.slug = 'low-fade'
WHERE b.slug = 'barber-studio-demo';
