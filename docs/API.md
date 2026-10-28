# API REST (Etapa 3)

Errores: siempre `application/problem+json` (RFC 7807). Validaciones: `400` con `errors` por campo; regla de negocio `422`; conflicto/duplicado `409`; sin sesión `401`; recurso de otra barbería o inexistente `404`.
Documentación interactiva: `/swagger-ui.html` (con la app en marcha).
Auth: cookie `access_token` (HttpOnly) emitida por `/api/auth/login`. Fechas en ISO-8601 UTC.

## Pública (sin login) — `/api/public/{slug}`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/shop` | Datos de la barbería, servicios activos y enlace de WhatsApp |
| GET | `/styles` | Estilos activos |
| GET | `/portfolio?style=&q=&page=&size=` | Galería: SOLO publicaciones `PUBLISHED` |
| GET | `/portfolio/{id}` | Detalle (404 si es borrador/archivada) |
| POST | `/requests` | Solicitud de corte (límite 5/hora por IP, honeypot `website`, no crea turno) |

## Privada (cookie de sesión) — `/api`
| Área | Rutas |
|---|---|
| Auth | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` |
| Configuración | `GET /settings`, `PUT /settings` (solo OWNER) |
| Clientes | `GET /clients?q=`, `POST /clients`, `GET/PUT /clients/{id}`, `POST /clients/{id}/archive`, `GET /clients/{id}/visits` |
| Servicios | `GET /services?activeOnly=`, `POST /services`, `PUT /services/{id}` |
| Estilos | `GET /styles?activeOnly=`, `POST /styles`, `PUT /styles/{id}` |
| Agenda | `GET /appointments?from=&to=` (fechas `yyyy-MM-dd`, hora de la barbería), `POST`, `GET/PUT /appointments/{id}`, `POST /{id}/confirm`, `/cancel`, `/complete`, `GET /{id}/visit` |
| Solicitudes | `GET /requests?status=`, `POST /requests/{id}/accept`, `POST /requests/{id}/reject` |
| Portfolio | `GET/POST /portfolio`, `GET/PUT /portfolio/{id}`, `POST /{id}/publish`, `/unpublish`, `/archive`, `POST /{id}/images` (multipart `file`, `alt`), `PUT /{id}/images/{imageId}/cover`, `DELETE /{id}/images/{imageId}` |
| Dashboard | `GET /dashboard/summary` |

## Reglas de la agenda
`PENDING → CONFIRMED | CANCELLED`, `CONFIRMED → COMPLETED | CANCELLED`. `COMPLETED` y `CANCELLED` son finales. Solo se pueden editar/reprogramar turnos abiertos. Completar crea la visita en la misma transacción y es idempotente. Cancelado nunca genera visita ni ingreso.

---
# Etapa 1: barberos, horarios, cierres y reservas

## Pública — `/api/public/{slug}`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/barbers` | Barberos activos y los servicios que realizan |
| GET | `/availability/days?serviceId=&barberId=&from=&to=` | Estado de cada día (`AVAILABLE`, `FULL`, `CLOSED` con motivo, `PAST`, `FAR`). `barberId` vacío = cualquier barbero. Máx. 62 días |
| GET | `/availability/slots?serviceId=&barberId=&date=` | Horarios libres de un día, calculados con la duración real del servicio |
| POST | `/bookings` | Reserva. Revalida el horario en el servidor; `409` si ya no está libre. Límite 6/hora por IP |

## Privada
| Área | Rutas |
|---|---|
| Barberos | `GET/POST /barbers`, `PUT/DELETE /barbers/{id}` (borrar solo sin historial; si no, desactivar) |
| Horario semanal | `GET/PUT /barbers/{id}/hours` (`{"items":[{weekday 1-7,startTime,endTime,breakStart?,breakEnd?}]}`) |
| Excepciones por fecha | `GET/POST /barbers/{id}/overrides`, `DELETE /barbers/{id}/overrides/{overrideId}` |
| Cierres y licencias | `GET /closures`, `POST /closures/preview`, `POST /closures`, `PUT/DELETE /closures/{id}` |
| Agenda | `GET /appointments?from=&to=&barberId=`, `POST /appointments/{id}/reassign`; `POST /appointments` ahora exige `barberId` |
| Ajustes | `GET/PUT /settings` incluye intervalo, preparación, confirmación automática, anticipación máxima y aviso mínimo |
| Portfolio | `DELETE /portfolio/{id}`; los estilos tienen `category` (`CORTE`, `COLOR`, `BARBA`, `TATUAJE`) |

## Cierres y turnos afectados
`POST /closures` devuelve `409` con `affected` (lista de turnos) si el bloqueo cae sobre turnos abiertos. Nunca se cancelan solos:
`authorizeAffected=true` crea el bloqueo dejando los turnos; `cancelAffected=true` (solo junto con la autorización) los cancela de forma explícita.

## Reglas de disponibilidad
Horario del barbero (excepción de la fecha, o semanal) − descanso − cierres generales − licencias del barbero − turnos existentes (más el tiempo de preparación). Los inicios se alinean al intervalo configurado y deben entrar completos antes del fin de la jornada. Un cierre general de día completo deja a todos sin disponibilidad; una licencia individual solo afecta a ese barbero. La base de datos impide el solapamiento por barbero aunque dos clientes reserven a la vez.

---
# Etapa 2 (parte 1)
| Cambio | Detalle |
|---|---|
| Clientes | `ClientRequest/Response` incluyen `birthDate` y `marketingOptIn` (autorización para recibir promociones; se puede revocar) |
| Portada | `POST /settings/logo` (multipart `file`, solo dueño) y `DELETE /settings/logo`; `GET /public/{slug}/shop` devuelve `logoUrl` |
| Migración | `V3__shop_cover_image.sql` agrega `logo_url` y `logo_key` a `barbershop` |

---
# Etapa 2 (parte 2): footer y destacados
| Cambio | Detalle |
|---|---|
| Ajustes | `instagramUrl`, `tiktokUrl`, `youtubeUrl`, `mapsUrl` (solo http/https), `neighborhood`, `city`, `hoursNote` |
| Público | `GET /public/{slug}/shop` devuelve esos datos y `openingHours`, calculado con los horarios de los barberos activos |
| Portfolio | `featured` y `sortOrder` en publicaciones; `POST /portfolio/{id}/feature` alterna el destacado. La galería pública muestra primero los destacados, luego por orden y fecha |
| Migración | `V4__footer_and_featured.sql` |
