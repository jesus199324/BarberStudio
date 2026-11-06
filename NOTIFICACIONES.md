# Aviso por email de nuevas reservas

Cuando un cliente reserva desde el sitio, el servidor guarda el turno (queda **PENDIENTE**) y, **después de guardarlo**,
te manda un email con los datos. Si el aviso falla, la reserva queda igual y el error se anota en el log de Render.

## Cómo se activa (gratis)
1. Creá una cuenta en https://resend.com (plan gratuito) con **el mismo email donde querés recibir los avisos**.
2. En Resend: *API Keys → Create API Key* (permiso "Sending access"). Copiá la clave (empieza con `re_`).
3. En Render, en tu servicio → *Environment*, agregá:

| Variable | Valor |
|---|---|
| `RESEND_API_KEY` | la clave `re_...` de Resend |
| `NOTIFY_EMAIL_TO` | tu email (si usás varios, separados por coma) |
| `NOTIFY_PANEL_URL` | (opcional) `https://tu-sitio/#/panel`, para el enlace dentro del email |
| `NOTIFY_EMAIL_FROM` | (opcional) por defecto `IKONOSTUDIO <onboarding@resend.dev>` |

4. Guardá y esperá que Render reinicie. En los logs vas a ver: `Avisos de nuevas reservas por email: ACTIVADOS.`

> Con el remitente de prueba `onboarding@resend.dev`, Resend solo entrega al email con el que creaste la cuenta.
> Para escribir desde `reservas@tudominio.com` (o a otros destinatarios) hay que verificar tu dominio en Resend, cuando lo compres.

Sin `RESEND_API_KEY` o sin `NOTIFY_EMAIL_TO` los avisos quedan desactivados y todo lo demás funciona igual.

## Cómo probar
- Hacé una reserva de prueba desde el sitio. Tiene que llegar un email con el asunto
  "💈 IKONOSTUDIO – Nueva solicitud de turno (pendiente de aprobación)". Revisá también *Spam* la primera vez.
- En el panel, la reserva figura como **Pendiente**; aprobarla o rechazarla funciona como siempre.
- Si no llega: Render → *Logs*, buscá "No se pudo enviar el aviso". El mensaje dice el motivo (clave inválida, remitente no permitido, etc.).

## Garantías
- Se envía una sola vez por reserva (y el proveedor descarta duplicados por clave de idempotencia `booking-<id>`).
- No se envía si la reserva no se guardó, ni para solicitudes de bots (campo trampa).
- Corre en otro hilo con tope de cola: un proveedor lento o caído no demora la respuesta al cliente.
- La clave de Resend vive solo en variables de entorno del servidor; nunca llega al navegador.
