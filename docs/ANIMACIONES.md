# Experiencia cinematográfica: qué hay y cómo ajustarla

Todo está hecho con **CSS y un script chico (`frontend/intro.js`), sin librerías**.

| Elemento | Dónde | Cómo se ajusta |
|---|---|---|
| Introducción (~2,4 s): monograma, nombre, descriptor, lema y viaje del logo al encabezado | `intro.js`, `#intro` en `index.html`, CSS "introduccion" | `config.js`: `intro: false` la desactiva; `monogramUrl` es tu monograma real (PNG transparente); `brandName`, `descriptor` |
| Se muestra una vez por sesión; se salta con un toque, Enter o Escape | `intro.js` | Se guarda `sessionStorage.intro` |
| Portada: contenido progresivo, zoom lento del fondo, fondo con profundidad | CSS `.hero-txt`, `.hero-bg` | — |
| Filosofía, Experiencia | `philHTML()`, `expHTML()` en `app.js` | Textos en `config.js`: `philosophy`, `pillars`, `experienceImage` |
| Servicios en tarjetas con botón Reservar | `pubHTML()` | Los datos salen del panel (Servicios) |
| Revelado por scroll con elementos escalonados | `reveal()` | — |
| Botones con línea dorada, galería con zoom suave | CSS | — |

## Accesibilidad y rendimiento
- `prefers-reduced-motion`: sin introducción, sin zoom ni desplazamientos; el contenido se ve de inmediato.
- Celulares: desplazamientos más cortos y sin zoom continuo.
- Las reservas no tienen animaciones de movimiento (solo aparecen); la lógica de disponibilidad no se tocó.

## Tema URBANO (antes "Skate", `data-theme=skate`; paletas: amarillo | naranja | acido en config.js → urbanPalette)
Paleta #0A0A0A / #252525 / #F1F0EB / #D93636, titulares en Anton. Solo CSS + JS propio (sin librerías).
- Intro estilo VHS/glitch con botón "Saltar" (una vez por sesión; se omite con movimiento reducido).
- Hero a pantalla completa "NO ES SOLO UN CORTE. ES TU ESTILO.", stickers, marquee, parallax (`data-par`), revelado con máscara al hacer scroll.
- Portfolio con filtros animados, microinteracciones, menú móvil animado, botón de WhatsApp y botón Reservar siempre visible.
- Cierre "TU ESTILO. TU IDENTIDAD. TU CORTE." y "Sobre mí" (texto en `config.js` → `aboutStory`).
- Respeta `prefers-reduced-motion`. El tema Shelby y toda la lógica de reservas quedan intactos.

## Fotos con movimiento al hacer scroll (ambos temas)
Cada foto de `.cw` (portfolio) se desliza dentro de su marco con la propiedad `translate` (`--pp` se calcula en el listener de scroll con requestAnimationFrame); las columnas pares van en sentido contrario. Se desactiva con `prefers-reduced-motion`.
Temas: **Clásico** (antes Shelby, `data-theme=shelby`) y **Urbano** (`data-theme=skate`). Intro de ~5 s en ambos.

## Tema Clásico
Se mantiene el estilo original. Único agregado: la última palabra del titular ("vos.") va en cursiva dorada.
Nota técnica: en los temas con recorte (`clip-path`), este va sobre la foto y no sobre la tarjeta, porque el observador de scroll no detecta elementos totalmente recortados.

## Equipo: foto y ficha del barbero
Cada barbero muestra su foto (o sus iniciales si no hay) y al tocarlo se abre una ficha con foto grande, especialidades y descripción. La foto se sube desde Panel → Barberos → Editar (JPG, PNG o WebP, hasta 5 MB) y la descripción es el campo "Descripción" del mismo formulario. Migración V7 (`photo_key`).
