package com.barberstudio.notification;

import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.common.util.WhatsAppLinks;
import com.barberstudio.shop.Barbershop;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Avisa al dueño cuando entra una reserva nueva. Se llama DESPUES de guardar la reserva, corre en otro hilo
 * y NUNCA lanza una excepcion: si el aviso falla, la reserva queda igual y el error se registra en el log.
 */
@Service
public class BookingNotifier {
    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);

    private final NotificationSender sender;
    private final String panelUrl;

    public BookingNotifier(NotificationSender sender, @Value("${app.notifications.panel-url:}") String panelUrl) {
        this.sender = sender;
        this.panelUrl = panelUrl;
    }

    @Async("notificationExecutor")
    public void newBooking(Barbershop shop, AppointmentResponse a, String clientPhone, String clientComment) {
        try {
            if (!sender.enabled()) {
                log.debug("Aviso de reserva {} omitido: notificaciones no configuradas.", a.id());
                return;
            }
            String waUrl = null;
            try { waUrl = WhatsAppLinks.link(clientPhone, "Hola " + firstName(a.clientName()) + ", te escribo de " + shop.getName() + " por tu solicitud de turno."); }
            catch (RuntimeException ignored) { /* telefono atipico: se omite el enlace */ }
            var n = new BookingNotification(a.id(), clean(shop.getName()), a.clientName(), clientPhone, null, waUrl,
                a.serviceName(), a.priceAgreed(), a.durationMinutes(), a.barberName(), a.startAt(),
                ZoneId.of(shop.getTimezone()), a.status() == AppointmentStatus.PENDING, clientComment, panelUrl);
            var msg = NotificationMessage.build(n);
            sender.send(msg.subject(), msg.text(), msg.html(), "booking-" + a.id());
            log.info("Aviso de nueva reserva {} enviado.", a.id());
        } catch (Throwable t) {   // ni siquiera un Error debe llegar al flujo de reservas
            log.error("No se pudo enviar el aviso de la reserva {}: {}", a == null ? "?" : a.id(), t.toString());
        }
    }

    private static String firstName(String n) { return n == null ? "" : n.trim().split("\\s+")[0]; }

    private static String clean(String n) { return n == null ? "" : n.replaceAll("[\\s_]+$", ""); }
}
