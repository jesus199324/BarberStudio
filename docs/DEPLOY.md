# Publicar gratis: Netlify + Render + Neon

```
Navegador ─► Netlify (frontend + proxy /api/*) ─► Render (API Spring Boot) ─► Neon (PostgreSQL)
```
Los planes gratuitos cambian: verificá límites vigentes antes de depender de ellos.

## Qué hace cada servicio y qué limitaciones tiene (a octubre de 2026)
| Servicio | Para qué | Limitación gratuita a tener presente |
|---|---|---|
| Netlify | Sirve el frontend y reenvía `/api/*` al backend | El proxy corta peticiones muy largas (~26 s): el primer pedido tras la inactividad puede fallar y hay que reintentar |
| Render (web service) | Corre el backend en Docker | Se duerme tras 15 min sin tráfico; despertar tarda ~1 minuto. 512 MB de RAM. **Su disco es efímero** |
| Neon | PostgreSQL | Plan gratuito permanente, 0,5 GB; se suspende tras 5 min sin uso (la primera consulta tarda 1–2 s) |
| Cloudinary (pendiente) | Guardar las fotos del portfolio | Necesario antes de subir fotos reales: hoy el backend solo guarda en disco local, que Render borra al reiniciar |

No uses la base PostgreSQL gratuita de Render: expira (30 días según su documentación actual).

## Pasos
1. **Neon**: creá un proyecto y copiá host, base, usuario y clave. Armá `DB_URL` así: `jdbc:postgresql://HOST/BASE?sslmode=require`.
2. **GitHub**: subí este repositorio (`git init`, `git add .`, `git commit`, `git push`). La pestaña *Actions* corre las pruebas.
3. **Render**: *New → Blueprint*, elegí el repositorio (usa `render.yaml`). Completá `DB_URL`, `DB_USER`, `DB_PASSWORD`, `BOOTSTRAP_*` y `ALLOWED_ORIGINS` (la URL de Netlify; podés volver a editarla después). `JWT_SECRET` se genera solo.
4. Al primer arranque, Flyway crea las tablas y `BootstrapInitializer` crea **tu barbería y tu usuario dueño** con los datos `BOOTSTRAP_*`. Después podés borrar `BOOTSTRAP_OWNER_PASSWORD` de Render.
5. **Netlify**: *Add new site → Import from Git*; la configuración sale de `netlify.toml`. Editá ahí `TU-BACKEND` con la URL de Render y volvé a desplegar.
6. Probá `https://TU-SITIO.netlify.app/api/public/TU-SLUG/shop`: debe devolver los datos de tu barbería (la primera vez puede tardar ~1 minuto).

## Tus datos en Render (variables BOOTSTRAP_*)
| Variable | Valor |
|---|---|
| `BOOTSTRAP_SHOP_NAME` | `jesuss.sk8` |
| `BOOTSTRAP_BARBER_NAME` | `Jesus Albornoz` |
| `BOOTSTRAP_WHATSAPP` | `+59899119628` |
| `BOOTSTRAP_OWNER_EMAIL` | el email con el que vas a entrar al panel |
| `BOOTSTRAP_OWNER_PASSWORD` | una clave tuya de 10+ caracteres (después borrá la variable) |

El identificador del sitio (`slug`) queda `jesuss-sk8`; es el que usa `frontend/config.js`.
Al primer arranque se crean tu barbería, tu usuario y tu barbero. **No hay horarios ni servicios cargados**: entrá al panel (`/#/panel`) y cargá Servicios, el Horario del barbero y los Ajustes. Hasta que cargues horarios, nadie puede reservar.

## Qué falta
- Cloudinary para guardar las fotos del portfolio (hoy van al disco de Render, que se borra al reiniciar).
- Fichas completas de clientes, fidelización, promociones, mensajes (cola de WhatsApp lista para enviar con un toque), reseñas y footer configurable: etapas 2 a 5.
- Verificar con el CI de GitHub (pestaña *Actions*) que el backend compila y pasa las pruebas: yo no pude ejecutarlo.
