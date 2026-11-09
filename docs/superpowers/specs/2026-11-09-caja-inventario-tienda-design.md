# Diseño: Caja, Inventario y Tienda de insumos — BarberStudio

Fecha: 2026-11-09 · Estado: borrador para revisión

## 1. Objetivo

Sumar a BarberStudio tres módulos que convierten la plataforma en un sistema de gestión completo para la barbería:

1. **Inventario**: productos de cuidado (ceras, aceites, shampoo, etc.) y su stock.
2. **Caja**: cobros de turnos y ventas rápidas, gastos y reportes de ingresos.
3. **Tienda pública**: catálogo de productos y pedido por WhatsApp.

**Para qué:** proyecto de portfolio que muestre perfil full stack con Java (lógica de negocio real, transacciones, roles, tests) y que además se use de verdad en la barbería.

**Éxito =** las tres etapas desplegadas (Netlify + Render + Neon), tests de integración en verde en el CI y README actualizado con capturas.

### Decisiones tomadas
| Tema | Decisión |
|---|---|
| Compra en la tienda | V1: carrito + pedido por WhatsApp, pago y entrega coordinados a mano. V2: Mercado Pago |
| Usuarios | Roles existentes `OWNER` / `BARBER` |
| Cobro de un turno | Al completar el turno se confirma monto (descuento/propina) y medio de pago |
| Ventas en persona | Productos dentro del cobro del turno **y** "venta rápida" independiente |
| Stock de pedidos web | Se valida al crear, se descuenta al **confirmar** |
| Frontend | JavaScript puro, separado en ES modules (sin build) |

### Fuera de alcance (V1)
Pago online, envíos con costo o seguimiento, variantes de producto (tamaños/aromas), proveedores, facturación electrónica, comisiones de barberos.

## 2. Modelo de datos

Todas las tablas llevan `id`, `created_at` (vía `BaseEntity`) y `barbershop_id`. Montos en `NUMERIC(10,2)`. Migraciones nuevas: `V9__inventory.sql`, `V10__cash.sql`, `V11__shop_orders.sql`.

### Inventario
**product**
| Columna | Tipo | Notas |
|---|---|---|
| name | VARCHAR(120) NOT NULL | único por barbería |
| description | TEXT | |
| price | NUMERIC(10,2) NOT NULL | `CHECK (price >= 0)` |
| stock | INT NOT NULL DEFAULT 0 | `CHECK (stock >= 0)` |
| low_stock_threshold | INT NOT NULL DEFAULT 2 | para marcar "stock bajo" |
| image_url | VARCHAR(500) | reutiliza `ImageStorage` |
| active | BOOLEAN NOT NULL DEFAULT true | inactivo = no se vende |
| visible_in_shop | BOOLEAN NOT NULL DEFAULT true | false = solo uso interno |
| version | BIGINT | bloqueo optimista |

**stock_movement**
| Columna | Tipo | Notas |
|---|---|---|
| product_id | FK product | |
| delta | INT NOT NULL | `CHECK (delta <> 0)`; + entra, − sale |
| reason | VARCHAR(20) | `SALE`, `ORDER`, `PURCHASE`, `ADJUSTMENT`, `VOID`, `ORDER_CANCEL` |
| sale_id / order_id | FK opcionales | de dónde vino el movimiento |
| user_id | FK app_user | quién lo hizo (null si fue el sistema) |
| note | VARCHAR(255) | obligatoria en `ADJUSTMENT` |

Regla: `product.stock` **solo** cambia a través de `StockService.move(...)`, que en la misma transacción bloquea el producto, valida, actualiza el stock e inserta el movimiento.

### Caja
**sale** (cobro)
| Columna | Tipo | Notas |
|---|---|---|
| barber_user_id | FK app_user NOT NULL | quién cobró |
| client_id | FK client | opcional |
| visit_id | FK visit UNIQUE | si viene de un turno |
| order_id | FK shop_order UNIQUE | si viene de un pedido web |
| payment_method | VARCHAR(20) NOT NULL | `CASH`, `TRANSFER` |
| total | NUMERIC(10,2) NOT NULL | suma de ítems |
| sold_at | TIMESTAMPTZ NOT NULL | |
| status | VARCHAR(10) NOT NULL | `ACTIVE`, `VOIDED` |
| voided_at, voided_by, void_reason | | se completan al anular |

**sale_item**
| Columna | Tipo | Notas |
|---|---|---|
| sale_id | FK sale | |
| kind | VARCHAR(10) | `SERVICE`, `PRODUCT` |
| product_id | FK product | solo si `PRODUCT` |
| description | VARCHAR(160) NOT NULL | instantánea del nombre |
| unit_price | NUMERIC(10,2) NOT NULL | instantánea del precio |
| quantity | INT NOT NULL | `CHECK (quantity > 0)` |

**expense** (gasto)
| Columna | Tipo | Notas |
|---|---|---|
| category | VARCHAR(20) | `SUPPLIES`, `TOOLS`, `TRANSPORT`, `OTHER` |
| amount | NUMERIC(10,2) NOT NULL | `CHECK (amount > 0)` |
| spent_on | DATE NOT NULL | |
| description | VARCHAR(255) | |
| user_id | FK app_user | quién lo cargó |

### Tienda
**shop_order**
| Columna | Tipo | Notas |
|---|---|---|
| public_code | INT | número correlativo por barbería, para el mensaje de WhatsApp |
| customer_name | VARCHAR(120) NOT NULL | |
| customer_whatsapp | VARCHAR(30) NOT NULL | normalizado con `Phone` |
| note | VARCHAR(500) | |
| total | NUMERIC(10,2) NOT NULL | |
| status | VARCHAR(12) NOT NULL | `PENDING`, `CONFIRMED`, `DELIVERED`, `CANCELLED` |
| confirmed_at, delivered_at, cancelled_at | TIMESTAMPTZ | |

**shop_order_item**: `order_id`, `product_id`, `product_name` (instantánea), `unit_price` (instantánea), `quantity > 0`.

## 3. Reglas de negocio

### Stock
- Nunca negativo: validación en `StockService` y `CHECK (stock >= 0)` en la base como última defensa.
- Concurrencia: `SELECT ... FOR UPDATE` sobre los productos involucrados, ordenados por id para evitar deadlocks.

### Completar turno (extiende `AppointmentService.complete`)
`CompleteRequest` suma `paymentMethod` (obligatorio) y `products: [{productId, quantity}]` (opcional).
En **una sola transacción**: crea la `visit` (como hoy) → crea la `sale` con un ítem `SERVICE` (precio = `finalPrice`) y los ítems `PRODUCT` → descuenta el stock. Si algo falla (por ejemplo, stock insuficiente), no se guarda nada.
Se mantiene la idempotencia: completar de nuevo devuelve la misma visita y no crea otro cobro.

### Venta rápida
`POST /api/sales` con ítems (productos y/o un servicio libre con descripción y precio), medio de pago y cliente opcional. Debe tener al menos un ítem.

### Anulación de cobro
Solo el dueño. `ACTIVE → VOIDED` con motivo obligatorio; devuelve el stock de los productos (`reason = VOID`). Los cobros nunca se borran. Anular un cobro ya anulado no tiene efecto (idempotente).

### Pedido web
```
PENDING ──confirm──> CONFIRMED ──deliver──> DELIVERED
   │                    │
   └──cancel──> CANCELLED <──cancel──┘
```
- **Crear:** solo productos activos, visibles y con `stock >= cantidad`. Máximo 10 líneas y 20 unidades por línea. No toca el stock. Precios tomados del servidor, nunca del cliente.
- **Confirmar:** revalida y descuenta el stock (`reason = ORDER`). Si ya no alcanza, error claro.
- **Entregar:** pide el medio de pago y crea la `sale` vinculada (`order_id`, sin volver a mover el stock).
- **Cancelar desde CONFIRMED:** devuelve el stock (`ORDER_CANCEL`). Desde PENDING no hay stock que devolver.
- Cualquier otra transición → `BusinessRuleException` → HTTP 422 (ya mapeado en `GlobalExceptionHandler`). Stock insuficiente también es 422.

### Reportes
`GET /api/reports/summary?from&to` → ingresos totales (solo cobros `ACTIVE`), separados en servicios y productos; por medio de pago; por barbero; gastos por categoría; ganancia = ingresos − gastos; top 5 productos por unidades. Fechas en la zona horaria de la barbería (America/Montevideo).

## 4. API

Panel (requiere login). La columna "Rol" indica quién puede usar cada ruta.

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/products?lowStock=` | OWNER, BARBER |
| POST | `/api/products` | OWNER |
| PUT | `/api/products/{id}` | OWNER |
| POST | `/api/products/{id}/stock` `{delta, reason: PURCHASE\|ADJUSTMENT, note}` | OWNER |
| GET | `/api/products/{id}/movements` | OWNER |
| POST | `/api/appointments/{id}/complete` (extendido) | OWNER, BARBER |
| POST | `/api/sales` | OWNER, BARBER |
| GET | `/api/sales?from&to` | BARBER ve solo los propios, OWNER ve todos |
| POST | `/api/sales/{id}/void` `{reason}` | OWNER |
| GET/POST | `/api/expenses` | OWNER |
| GET | `/api/reports/summary?from&to` | OWNER |
| GET | `/api/orders?status=` | OWNER, BARBER |
| POST | `/api/orders/{id}/confirm` | OWNER, BARBER |
| POST | `/api/orders/{id}/deliver` `{paymentMethod}` | OWNER, BARBER |
| POST | `/api/orders/{id}/cancel` | OWNER, BARBER |

Público (sin login):

| Método | Ruta | Notas |
|---|---|---|
| GET | `/api/public/{slug}/products` | solo activos, visibles y con stock > 0 |
| POST | `/api/public/{slug}/orders` | con `RateLimiter`; responde `{code, total, whatsappUrl}` armado con `WhatsAppLinks` |

**Permisos:** se habilita `@EnableMethodSecurity` y se usa `@PreAuthorize("hasRole('OWNER')")` en los endpoints solo para el dueño. Hay que revisar que los endpoints existentes sigan funcionando igual (tests de regresión).

## 5. Frontend (JavaScript puro, ES modules)

- `app.js` pasa a cargar módulos con `<script type="module">`. Los helpers compartidos (fetch con JWT, formateo de pesos, toasts) se mueven a `api.js` / `ui.js`.
- `caja.js`: resumen del día, lista de cobros, venta rápida; para el dueño, pestañas de Gastos y Reporte.
- `inventario.js`: tabla de productos, stock bajo en rojo, reponer/ajustar, historial.
- `pedidos.js`: pedidos por estado, acciones, contador de pendientes.
- Formulario de completar turno: medio de pago, agregar productos y total en vivo.
- `tienda.js` (sitio público): grilla, carrito en `localStorage` (con try/catch), formulario y redirección a WhatsApp.
- Las secciones solo para el dueño se ocultan para el barbero; igual las protege el backend.

## 6. Tests

Integración con Testcontainers (siguiendo `ApiFlowIntegrationTest`):
- **Stock:** vender más de lo disponible → error; anular cobro y cancelar pedido confirmado → el stock vuelve; cada cambio deja un `stock_movement`.
- **Completar turno con productos:** crea visita + cobro + movimientos; con stock insuficiente no crea nada (rollback); completar dos veces no duplica el cobro.
- **Pedido:** recorrido feliz completo; transiciones inválidas → error; precios del cliente ignorados.
- **Permisos:** BARBER recibe 403 en gastos, reportes, anulación y edición de productos; BARBER solo ve sus cobros.
- **Reportes:** totales correctos con cobros anulados excluidos.
- **Concurrencia:** dos confirmaciones simultáneas del último producto → solo una tiene éxito.

## 7. Etapas

0. **Verificar la base:** `mvn test` y CI en verde con el código actual (el README indica que no se verificó por ejecución). Arreglar lo que falle antes de seguir.
1. **Inventario:** migración V9, productos, `StockService`, endpoints, pantalla de inventario, permisos por rol.
2. **Caja:** migración V10, completar turno extendido, venta rápida, anulación, gastos, reportes y pantallas.
3. **Tienda:** migración V11, catálogo público, pedidos, flujo de estados y pantallas de pedidos y tienda.
4. **V2 (aparte):** Mercado Pago.

Cada etapa termina desplegada, con tests en verde y una entrada en el README.
