# Etapas 0 y 1: Verificar la base + Inventario — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Confirmar que BarberStudio compila y sus tests pasan, y agregar el módulo de Inventario (productos, stock con historial, foto y pantalla en el panel).

**Architecture:** Nuevo paquete `com.barberstudio.inventory` con el mismo estilo que `client/` (Controller → Service con records Request/Response → Repository, filtrado por `CurrentUser.shopId()`). Todo cambio de stock pasa por `StockService.move(...)`, que bloquea la fila (`PESSIMISTIC_WRITE`, como `AppointmentRepository.lockById`), valida y registra un `stock_movement` en la misma transacción. En el frontend, los helpers de `app.js` pasan a `lib.js` y cada sección nueva vive en su propio ES module que registra sus pestañas, acciones y formularios.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Security (JWT con claim `role`), JPA + Flyway, PostgreSQL 16, Testcontainers + MockMvc, JavaScript puro con ES modules.

**Spec:** `docs/superpowers/specs/2026-11-09-caja-inventario-tienda-design.md` (secciones 2 "Inventario", 3 "Stock", 4 "API" filas de `/api/products`, 5 `inventario.js`, 7 etapas 0 y 1).

## Global Constraints

- Montos `NUMERIC(10,2)`; en Java `BigDecimal` con `@DecimalMin("0") @Digits(integer = 8, fraction = 2)`.
- Toda consulta privada filtra por `barbershop_id = CurrentUser.shopId()`; un recurso de otra barbería responde **404**, no 403.
- Solo dueño: `@PreAuthorize("hasRole('OWNER')")` (patrón de `SettingsController`). Rol insuficiente → 403.
- Errores: regla de negocio → `BusinessRuleException` (422); duplicado → `ConflictException` (409); no encontrado → `NotFoundException` (404); validación → 400. Mensajes en español rioplatense ("Ingresá", "No tenés").
- `product.stock` cambia **solo** vía `StockService.move`. Ningún otro código hace `setStock`.
- Migraciones nuevas empiezan en `V9__`. No modificar migraciones existentes.
- Frontend sin build ni dependencias nuevas.
- Mensajes de commit en español, formato `tipo: descripción`.

## Review Focus

1. Usuario de la barbería B pide `/api/products/{id}` de la barbería A → 404 sin filtrar datos (Task 2).
2. Ajuste que dejaría stock negativo → 422 "Stock insuficiente: quedan N." y el stock no cambia ni se crea movimiento (Task 1).
3. Nombre de producto repetido con distinta mayúscula/espacios ("Cera Mate" vs " cera mate") en la misma barbería → 409; el mismo nombre en otra barbería se permite (Task 2).
4. Precio negativo o con 3 decimales → 400 (Task 2).
5. Ajuste `ADJUSTMENT` sin nota, `delta = 0` o motivo de sistema (`SALE`) enviado por la API → 400/422 (Task 2).

---

### Task 0: Verificar que la base compila y los tests pasan

**Files:**
- Modify (solo si hace falta): lo que el diagnóstico indique.
- Modify: `README.md` (tabla "Estado", fila `backend/`).

- [ ] **Step 1: Correr los tests**

Run: `cd backend && mvn -B test` (requiere Docker en marcha)
Expected: `BUILD SUCCESS` con `ApiFlowIntegrationTest`, `SchemaIntegrationTest` y `NotificationTest` en verde.

- [ ] **Step 2: Si algo falla, diagnosticar antes de tocar código**

Usar superpowers:systematic-debugging. Cada arreglo, con un test que lo cubra. No avanzar a la Task 1 con tests en rojo.

- [ ] **Step 3: Actualizar el README**

En la fila `backend/` reemplazar "todavía sin verificar por ejecución: mirá el CI" por "verificada: `mvn test` en verde" (y la fecha). Si el README dice que el CI revisa el JavaScript, dejarlo así: la Task 4 agrega ese paso.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "chore: verificar build y tests del backend"
```

---

### Task 1: Esquema de inventario y `StockService`

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__inventory.sql`
- Create: `backend/src/main/java/com/barberstudio/inventory/Product.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/ProductRepository.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/StockMovement.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/StockMovementRepository.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/MovementReason.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/StockService.java`
- Create: `backend/src/test/java/com/barberstudio/support/IntegrationTestBase.java`
- Create: `backend/src/test/java/com/barberstudio/inventory/StockServiceTest.java`
- Modify: `docs/ERD.md` (agregar `PRODUCT ||--o{ STOCK_MOVEMENT`, `BARBERSHOP ||--o{ PRODUCT`)

**Interfaces:**
- Produces:
  - `enum MovementReason { SALE, ORDER, PURCHASE, ADJUSTMENT, VOID, ORDER_CANCEL }`
  - `record StockChange(long shopId, long productId, int delta, MovementReason reason, Long userId, Long saleId, Long orderId, String note)`
  - `StockMovement StockService.move(StockChange c)` — `@Transactional`; lanza `NotFoundException("Producto no encontrado.")` si no existe en esa barbería, `BusinessRuleException("Stock insuficiente: quedan " + stock + ".")` si `stock + delta < 0`, `IllegalArgumentException` si `delta == 0`.
  - `Optional<Product> ProductRepository.lockById(Long id, Long shop)` con `@Lock(PESSIMISTIC_WRITE)`.
  - `Optional<Product> ProductRepository.findByIdAndBarbershopId(Long id, Long shop)`.
  - `List<StockMovement> StockMovementRepository.findTop100ByProductIdOrderByCreatedAtDescIdDesc(Long productId)`.
  - Test base: `abstract class IntegrationTestBase` con el contenedor PG estático (mismo patrón que `ApiFlowIntegrationTest`), `@Autowired MockMvc mvc`, `JdbcTemplate jdbc`, y helpers `long seedShop(String slug)`, `long seedUser(long shopId, String email, String role)`, `Cookie login(String email)`, `MvcResult postJson(Cookie c, String url, String body)` (espera 2xx), `long id(MvcResult r)`, `void truncateAll()` (`TRUNCATE barbershop RESTART IDENTITY CASCADE`).

- [ ] **Step 1: Escribir la migración `V9__inventory.sql`**

```sql
CREATE TABLE product (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id       BIGINT        NOT NULL REFERENCES barbershop(id),
    name                VARCHAR(120)  NOT NULL,
    description         TEXT,
    price               NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock               INTEGER       NOT NULL DEFAULT 0 CHECK (stock >= 0),
    low_stock_threshold INTEGER       NOT NULL DEFAULT 2 CHECK (low_stock_threshold >= 0),
    image_url           VARCHAR(500),
    image_key           VARCHAR(300),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    visible_in_shop     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_product_shop_name ON product (barbershop_id, lower(trim(name)));

CREATE TABLE stock_movement (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    barbershop_id BIGINT       NOT NULL REFERENCES barbershop(id),
    product_id    BIGINT       NOT NULL REFERENCES product(id),
    delta         INTEGER      NOT NULL CHECK (delta <> 0),
    reason        VARCHAR(20)  NOT NULL CHECK (reason IN ('SALE','ORDER','PURCHASE','ADJUSTMENT','VOID','ORDER_CANCEL')),
    sale_id       BIGINT,      -- FK se agrega en V10 (tabla sale)
    order_id      BIGINT,      -- FK se agrega en V11 (tabla shop_order)
    user_id       BIGINT       REFERENCES app_user(id),
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_movement_product ON stock_movement (product_id, created_at DESC);
```

- [ ] **Step 2: Escribir los tests que fallan** en `StockServiceTest extends IntegrationTestBase`

Crear el producto por `jdbc` (stock inicial 1) y llamar a `StockService` directamente.

```java
@Test void moveUpdatesStockAndRecordsMovement()      // +5 PURCHASE → stock 6, 1 movimiento con delta 5, reason PURCHASE
@Test void moveRejectsNegativeStockAndChangesNothing() // -2 → BusinessRuleException con mensaje "Stock insuficiente: quedan 1."; stock sigue 1; 0 movimientos
@Test void moveFromOtherShopIsNotFound()              // shopId de la barbería B sobre producto de A → NotFoundException
@Test void zeroDeltaIsRejected()                      // delta 0 → IllegalArgumentException
@Test void concurrentSalesOfLastUnitOnlyOneSucceeds() // 2 hilos, -1 cada uno sobre stock 1 (CountDownLatch para arrancar juntos):
                                                      // exactamente 1 éxito y 1 BusinessRuleException; stock final 0; 1 movimiento
```

- [ ] **Step 3: Correr y ver que fallan**

Run: `cd backend && mvn -B test -Dtest=StockServiceTest`
Expected: FAIL (no compila: `StockService` no existe).

- [ ] **Step 4: Implementar entidades, repositorios y `StockService.move`**

`Product` y `StockMovement` extienden `BaseEntity` con Lombok como `Visit`. `move` usa `lockById`, valida, `product.setStock(stock + delta)` y guarda el movimiento. El setter de stock queda package-private para que solo `StockService` (mismo paquete) lo use.

- [ ] **Step 5: Correr y ver que pasan**

Run: `cd backend && mvn -B test -Dtest=StockServiceTest`
Expected: PASS (5 tests). Después `mvn -B test` completo, todo en verde (`SchemaIntegrationTest` incluido).

- [ ] **Step 6: Commit**

```bash
git add backend docs/ERD.md && git commit -m "feat: esquema de inventario y servicio de stock con historial"
```

---

### Task 2: API de productos con permisos

**Files:**
- Create: `backend/src/main/java/com/barberstudio/inventory/ProductService.java`
- Create: `backend/src/main/java/com/barberstudio/inventory/ProductController.java`
- Create: `backend/src/test/java/com/barberstudio/inventory/ProductApiTest.java`
- Modify: `docs/API.md` (sección "Inventario")

**Interfaces:**
- Consumes: `StockService.move`, `StockChange`, `MovementReason`, `ProductRepository`, `IntegrationTestBase` (Task 1).
- Produces (records dentro de `ProductService`):
  - `record ProductRequest(@NotBlank @Size(max=120) String name, @Size(max=2000) String description, @NotNull @DecimalMin("0") @Digits(integer=8, fraction=2) BigDecimal price, @Min(0) Integer lowStockThreshold, Boolean active, Boolean visibleInShop)` — null en los opcionales = valor por defecto (2, true, true).
  - `record ProductResponse(Long id, String name, String description, BigDecimal price, int stock, int lowStockThreshold, boolean lowStock, String imageUrl, boolean active, boolean visibleInShop)` — `lowStock = stock <= lowStockThreshold`.
  - `record StockAdjustRequest(@NotNull Integer delta, @NotNull MovementReason reason, @Size(max=255) String note)`.
  - `record MovementResponse(Long id, int delta, MovementReason reason, String note, Long userId, Instant createdAt)`.
- Endpoints:
  | Método | Ruta | Rol | Respuesta |
  |---|---|---|---|
  | GET | `/api/products?lowStock=true` | autenticado | `List<ProductResponse>` ordenada por nombre; incluye inactivos |
  | POST | `/api/products` | OWNER | 201 `ProductResponse`, stock 0 |
  | PUT | `/api/products/{id}` | OWNER | `ProductResponse` (no toca stock) |
  | POST | `/api/products/{id}/stock` | OWNER | `ProductResponse` con stock nuevo |
  | GET | `/api/products/{id}/movements` | OWNER | `List<MovementResponse>` (últimos 100) |

- [ ] **Step 1: Escribir los tests que fallan** en `ProductApiTest extends IntegrationTestBase`

Seed: barbería A con OWNER `a@test.com` y BARBER `barber@test.com`; barbería B con OWNER `b@test.com`.

```java
@Test void ownerCreatesProductWithZeroStock()         // POST {"name":"Cera mate","price":250} → 201, stock 0, lowStock true, active true, visibleInShop true
@Test void purchaseAndAdjustmentUpdateStock()          // +10 PURCHASE → 10; -2 ADJUSTMENT nota "Rotura" → 8; movements: 2 ítems, el más nuevo primero
@Test void adjustmentRequiresNote()                    // ADJUSTMENT sin nota → 422 "Indicá el motivo del ajuste."
@Test void apiRejectsSystemReasons()                   // reason SALE/ORDER/VOID/ORDER_CANCEL → 422 "Motivo no permitido."
@Test void zeroDeltaRejected()                          // delta 0 → 422 "La cantidad no puede ser 0."
@Test void cannotLeaveNegativeStock()                  // stock 1, -2 → 422 "Stock insuficiente: quedan 1."; GET muestra stock 1
@Test void duplicateNameIgnoresCaseAndSpaces()         // "Cera Mate" y luego " cera mate" → 409 "Ya existe un producto con ese nombre."
@Test void sameNameAllowedInOtherShop()                // B crea "Cera Mate" → 201
@Test void invalidPriceRejected()                       // price -1 → 400; price 10.555 → 400
@Test void otherShopGets404()                           // owner B: GET movements / PUT / POST stock del producto de A → 404
@Test void barberCanListButNotManage()                  // barber: GET /api/products 200; POST, PUT, /stock, /movements → 403
@Test void lowStockFilter()                             // stocks 0, 2, 5 con umbral 2 → ?lowStock=true devuelve los de 0 y 2
@Test void unauthenticatedGets401()                     // GET /api/products sin cookie → 401
```

- [ ] **Step 2: Correr y ver que fallan**

Run: `cd backend && mvn -B test -Dtest=ProductApiTest`
Expected: FAIL (404 en todas las rutas o no compila).

- [ ] **Step 3: Implementar `ProductService` y `ProductController`**

Nombre: `trim()` antes de guardar; duplicado detectado con `existsByBarbershopIdAndNameIgnoreCase(shop, name.trim())` (y excluyendo el propio id en PUT) → `ConflictException`. El índice único queda como defensa. `/stock` solo acepta `PURCHASE` y `ADJUSTMENT`; `PURCHASE` exige `delta > 0`; arma `StockChange` con `CurrentUser.userId()`. `@PreAuthorize("hasRole('OWNER')")` en los métodos de solo dueño del controller.

- [ ] **Step 4: Correr y ver que pasan**

Run: `cd backend && mvn -B test`
Expected: PASS, todos los tests (los 13 nuevos y los existentes).

- [ ] **Step 5: Documentar en `docs/API.md`** la tabla de endpoints de arriba, con un ejemplo de request/response de `POST /api/products/{id}/stock`.

- [ ] **Step 6: Commit**

```bash
git add backend docs/API.md && git commit -m "feat: API de productos y ajustes de stock con permisos por rol"
```

---

### Task 3: Foto del producto

**Files:**
- Modify: `backend/src/main/java/com/barberstudio/inventory/ProductService.java`
- Modify: `backend/src/main/java/com/barberstudio/inventory/ProductController.java`
- Test: `backend/src/test/java/com/barberstudio/inventory/ProductApiTest.java`

**Interfaces:**
- Consumes: `ImageStorage.store(long shopId, byte[] bytes, String ext)`, `ImageStorage.delete(String key)`, `ImageValidator.extensionFor(byte[])` (paquete `portfolio`; hacer `public` lo que haga falta).
- Produces: `POST /api/products/{id}/image` (multipart `file`, OWNER) → `ProductResponse` con `imageUrl`; `DELETE /api/products/{id}/image` (OWNER) → 204.

- [ ] **Step 1: Tests que fallan**

```java
@Test void ownerUploadsAndReplacesImage()   // sube PNG válido → imageUrl no nulo; sube otro → imageUrl distinto y el archivo anterior se borra (verificar que ya no existe en ./target/test-uploads)
@Test void rejectsNonImageFile()            // bytes de texto → 400 o 422 (el mismo código que hoy devuelve el portfolio para archivos inválidos)
@Test void barberCannotUploadImage()        // → 403
```

Usar el mismo PNG mínimo que ya usa `ApiFlowIntegrationTest` para el portfolio.

- [ ] **Step 2: Correr y ver que fallan** — `mvn -B test -Dtest=ProductApiTest` → FAIL (404).

- [ ] **Step 3: Implementar** siguiendo `PortfolioService.addImage`: guardar el archivo, actualizar `image_url`/`image_key`, borrar la imagen anterior **después** de guardar en la base, y borrar el archivo nuevo si falla la base.

- [ ] **Step 4: Correr y ver que pasan** — `mvn -B test` → PASS.

- [ ] **Step 5: Commit**

```bash
git add backend && git commit -m "feat: foto de producto"
```

---

### Task 4: Frontend en módulos (`lib.js`) sin cambiar comportamiento

**Files:**
- Create: `frontend/lib.js`
- Create: `frontend/package.json` → `{ "private": true, "type": "module" }` (solo para que `node --check` entienda `import`)
- Modify: `frontend/app.js` (quitar helpers movidos, agregar `import`, exponer registro de secciones)
- Modify: `frontend/index.html` (`<script type="module" src="app.js"></script>`)
- Modify: `.github/workflows/main.yml` (paso nuevo)

**Interfaces:**
- Produces, desde `lib.js` (mismos cuerpos que hoy en `app.js`): `export { CFG, LOCAL, API, SLUG, TZ, OFF, $, esc, money, hm, dlong, ymd, pad, toISO, imgUrl, api, dlg, dopen, dclose, toast, fv, sel }`.
- Registro de secciones en `app.js`: `function register(mod)` donde `mod = { tabs?: [[key, label, ownerOnly]], views?: {key: async () => html}, actions?: {name: (dataset, el) => void}, forms?: {name: (form) => void} }`. Hace `TABS.push(...)`, `Object.assign(T, mod.views)`, `Object.assign(A, mod.actions)`, `Object.assign(F, mod.forms)`. Las pestañas con `ownerOnly = true` se muestran solo si `S.me.role === 'OWNER'`.
- `lib.js` también exporta `export const panel = { reload: () => {}, me: () => null }`; `app.js` asigna `panel.reload = loadPanel` y `panel.me = () => S.me`.

`/api/auth/me` ya devuelve `role` (`AuthController.MeResponse`), así que `S.me.role` está disponible sin cambios en el backend.

- [ ] **Step 2: Mover los helpers a `lib.js` y convertir `app.js` en módulo**

Sin cambios de lógica. Los `const`/`let` que ya están declarados funcionan igual en modo estricto (no hay `onclick=` inline ni globals implícitos; verificado).

- [ ] **Step 3: Agregar el chequeo al CI**, después de "Build with Maven":

```yaml
    - name: Revisar sintaxis del JavaScript
      run: for f in frontend/*.js; do node --check "$f"; done
```

Run local: `for f in frontend/*.js; do node --check "$f"; done`
Expected: sin salida (exit 0).

- [ ] **Step 4: Prueba manual de regresión**

Levantar como indica el README (`docker compose up -d`, `mvn spring-boot:run -Dspring-boot.run.profiles=dev`, `python3 -m http.server 5173`). Verificar: sitio público carga, reserva de punta a punta, login en `/#/panel`, todas las pestañas existentes abren, completar un turno. Consola del navegador sin errores.

- [ ] **Step 5: Commit**

```bash
git add frontend .github && git commit -m "refactor: frontend en ES modules y chequeo de sintaxis en CI"
```

---

### Task 5: Pantalla de Inventario (`inventario.js`)

**Files:**
- Create: `frontend/inventario.js`
- Modify: `frontend/app.js` (una línea: `import inventario from './inventario.js'; register(inventario);`)
- Modify: `frontend/app.css` (solo clases nuevas con prefijo `inv-`)

**Interfaces:**
- Consumes: helpers de `lib.js`, `register` (Task 4), endpoints de Task 2 y Task 3.
- Produces: `export default { tabs: [['inventario', 'Inventario', false]], views: { inventario }, actions: {...}, forms: {...} }`. Los nombres de acciones y formularios llevan prefijo `inv` (`invnew`, `invedit`, `invstock`, `invhist`, `invimg`) para no chocar con los existentes.

- [ ] **Step 1: Vista de lista**

Tabla: foto (miniatura), nombre, precio con `money()`, stock (clase `inv-low` en rojo si `lowStock`), badges "Inactivo" / "No visible en tienda". Filtro "Solo stock bajo" (`?lowStock=true`). Si `panel.me().role !== 'OWNER'`, la tabla es solo lectura (sin botones).

- [ ] **Step 2: Diálogos del dueño**

- Nuevo/Editar producto: nombre, descripción, precio, umbral de stock bajo, activo, visible en tienda; input de archivo para la foto.
- Mover stock: selector "Reposición (+)" / "Ajuste (+/−)", cantidad, nota (obligatoria en ajuste). Muestra el stock actual y cómo queda.
- Historial: lista de movimientos con fecha (`dlong` + `hm`), +/−, motivo traducido (`{SALE:'Venta', ORDER:'Pedido web', PURCHASE:'Reposición', ADJUSTMENT:'Ajuste', VOID:'Cobro anulado', ORDER_CANCEL:'Pedido cancelado'}`) y nota.
- Los errores del backend se muestran con `toast(e.message, 1)`; después de cada acción, `panel.reload()`.

- [ ] **Step 3: Prueba manual**

Como OWNER (perfil dev): crear "Cera mate" $250 → reponer +5 → ajustar −1 "Rotura" → historial con 2 líneas → subir foto → filtrar stock bajo. Intentar un ajuste de −10 → mensaje "Stock insuficiente: quedan 4.". Crear un usuario BARBER en la base local: ve la lista, no ve los botones. `node --check frontend/inventario.js` sin errores.

- [ ] **Step 4: README**

Agregar "Inventario" a la descripción del panel y una captura en `docs/` (o anotar como pendiente si no se puede sacar).

- [ ] **Step 5: Commit**

```bash
git add frontend README.md docs && git commit -m "feat: pantalla de inventario en el panel"
```

---

## Cierre de la etapa

- [ ] `mvn -B test` en verde y `node --check` sin errores.
- [ ] Deploy (Render + Netlify, según `docs/DEPLOY.md`) y prueba rápida en producción: crear un producto y moverle el stock.
- [ ] Post corto en LinkedIn con una captura (opcional, pero suma a la marca personal).
