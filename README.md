# Barber Studio

Plataforma para barbería (jesuss.sk8): sitio público con reservas por calendario y portfolio, y panel de gestión (agenda, barberos, horarios, cierres, servicios, clientes, portfolio y ajustes).

## Estado
| Parte | Estado |
|---|---|
| `backend/` | API REST (Spring Boot, PostgreSQL, JWT): barberos, horarios, cierres, motor de disponibilidad, reservas, clientes, servicios, portfolio. Escrita con pruebas de integración, **todavía sin verificar por ejecución**: mirá el CI |
| `frontend/index.html` + `app.js` | Sitio y panel **reales**, conectados a la API (sin build: JavaScript puro) |
| `frontend/demo.html` | Maqueta con datos simulados, solo como referencia visual (quiz de cortes, sugerencias por rostro, combos). No es el producto |
| Despliegue | Netlify + Render + Neon, preparado (`docs/DEPLOY.md`) |
| Pendiente | Cloudinary, fichas de clientes, fidelización, promociones, mensajes, reseñas, footer configurable |

## Probar en local
```bash
cp .env.example .env && docker compose up -d
cd backend && export DB_PASSWORD=<la de .env>
mvn spring-boot:run -Dspring-boot.run.profiles=dev          # API en :8080, Swagger en /swagger-ui.html
# en otra terminal:
cd frontend && python3 -m http.server 5173                   # sitio en http://localhost:5173  (panel: /#/panel)
```
Perfil `dev`: barbería `barber-studio-demo`, dos barberos con horario martes a sábado, usuario `demo@barberstudio.test` / `demo1234`.
Si ya habías levantado una base con versiones anteriores, reiniciala: `docker compose down -v`.

## Pruebas
`cd backend && mvn test` (requiere Docker). El CI de GitHub corre las pruebas del backend y revisa la sintaxis del JavaScript.

## Publicar gratis
Ver `docs/DEPLOY.md`. Documentación: `docs/API.md`, `docs/ERD.md`.
