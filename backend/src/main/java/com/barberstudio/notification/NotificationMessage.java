package com.barberstudio.notification;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Arma el asunto y el cuerpo (texto y HTML) del aviso. Funcion pura: facil de probar. */
public final class NotificationMessage {
    private NotificationMessage() {}

    private static final Locale ES_UY = Locale.forLanguageTag("es-UY");

    public record Message(String subject, String text, String html) {}

    public static Message build(BookingNotification n) {
        String brand = n.shopName() == null || n.shopName().isBlank() ? "IKONOSTUDIO" : n.shopName().trim().toUpperCase(ES_UY);
        String when = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES_UY).format(n.startAt().atZone(n.zone()));
        when = Character.toUpperCase(when.charAt(0)) + when.substring(1);
        String hour = DateTimeFormatter.ofPattern("HH:mm", ES_UY).format(n.startAt().atZone(n.zone()));
        String estado = n.pendingApproval() ? "PENDIENTE DE APROBACIÓN" : "CONFIRMADO AUTOMÁTICAMENTE";
        String titulo = n.pendingApproval() ? "NUEVA SOLICITUD DE TURNO" : "NUEVO TURNO CONFIRMADO";
        String subject = "💈 " + brand + " – " + (n.pendingApproval() ? "Nueva solicitud de turno (pendiente de aprobación)" : "Nuevo turno confirmado")
            + ": " + clean(n.clientName()) + ", " + hour;

        StringBuilder t = new StringBuilder();
        t.append("💈 ").append(brand).append(" – ").append(titulo).append("\n\n");
        t.append("🔔 Estado: ").append(estado).append("\n\n");
        t.append("👤 Datos del cliente:\n");
        t.append("- Nombre: ").append(clean(n.clientName())).append('\n');
        t.append("- Teléfono: ").append(clean(n.clientPhone())).append('\n');
        if (has(n.clientEmail())) t.append("- Email: ").append(clean(n.clientEmail())).append('\n');
        t.append("\n✂️ Servicio solicitado:\n");
        t.append("- Servicio: ").append(clean(n.serviceName())).append('\n');
        t.append("- Precio: ").append(money(n.price())).append('\n');
        t.append("- Duración: ").append(n.durationMinutes()).append(" min\n");
        if (has(n.barberName())) t.append("- Barbero: ").append(clean(n.barberName())).append('\n');
        t.append("\n📅 Datos de la reserva:\n");
        t.append("- Fecha: ").append(when).append('\n');
        t.append("- Hora: ").append(hour).append('\n');
        if (has(n.clientComment())) t.append("\n📝 Comentario: ").append(clean(n.clientComment())).append('\n');
        if (n.pendingApproval()) {
            t.append("\n⚠️ IMPORTANTE: Este turno todavía no está confirmado. Requiere aprobación desde el panel administrativo.\n");
        } else {
            t.append("\nℹ️ Este turno se confirmó automáticamente porque tenés activada la confirmación automática.\n");
        }
        if (has(n.panelUrl())) t.append("\nPanel: ").append(n.panelUrl().trim()).append('\n');
        if (has(n.clientWhatsappUrl())) t.append("Escribirle por WhatsApp: ").append(n.clientWhatsappUrl()).append('\n');
        String text = t.toString();

        StringBuilder h = new StringBuilder();
        h.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1a1a1a\">");
        h.append("<h2 style=\"margin:0 0 4px\">💈 ").append(esc(brand)).append("</h2>");
        h.append("<p style=\"margin:0 0 14px;letter-spacing:.06em;color:#8a6d3b\"><b>").append(titulo).append("</b></p>");
        h.append("<p style=\"background:").append(n.pendingApproval() ? "#fff4d6" : "#e3f5e6").append(";padding:10px 12px;border-radius:6px;margin:0 0 14px\">🔔 <b>Estado: ")
            .append(estado).append("</b></p>");
        h.append(section("👤 Datos del cliente",
            row("Nombre", n.clientName()), row("Teléfono", n.clientPhone()), has(n.clientEmail()) ? row("Email", n.clientEmail()) : ""));
        h.append(section("✂️ Servicio solicitado",
            row("Servicio", n.serviceName()), row("Precio", money(n.price())), row("Duración", n.durationMinutes() + " min"),
            has(n.barberName()) ? row("Barbero", n.barberName()) : ""));
        h.append(section("📅 Datos de la reserva", row("Fecha", when), row("Hora", hour)));
        if (has(n.clientComment())) h.append("<p><b>📝 Comentario:</b> ").append(esc(n.clientComment())).append("</p>");
        h.append("<p style=\"border-left:4px solid ").append(n.pendingApproval() ? "#d9534f" : "#5cb85c").append(";padding:6px 10px;background:#f6f6f6\">")
            .append(n.pendingApproval()
                ? "⚠️ <b>IMPORTANTE:</b> Este turno todavía no está confirmado. Requiere aprobación desde el panel administrativo."
                : "ℹ️ Este turno se confirmó automáticamente porque tenés activada la confirmación automática.").append("</p>");
        if (has(n.panelUrl())) h.append("<p><a href=\"").append(esc(n.panelUrl().trim())).append("\">Abrir el panel</a></p>");
        if (has(n.clientWhatsappUrl())) h.append("<p><a href=\"").append(esc(n.clientWhatsappUrl())).append("\">Escribirle por WhatsApp</a></p>");
        h.append("</div>");
        return new Message(subject, text, h.toString());
    }

    private static String section(String title, String... rows) {
        StringBuilder s = new StringBuilder("<h3 style=\"margin:14px 0 6px\">").append(title).append("</h3><table style=\"border-collapse:collapse\">");
        for (String r : rows) s.append(r);
        return s.append("</table>").toString();
    }

    private static String row(String k, String v) {
        return "<tr><td style=\"padding:2px 14px 2px 0;color:#666\">" + esc(k) + "</td><td style=\"padding:2px 0\"><b>" + esc(v) + "</b></td></tr>";
    }

    static String money(BigDecimal p) {
        if (p == null) return "A consultar";
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(ES_UY));
        f.setMaximumFractionDigits(2);
        return "$ " + f.format(p);
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }

    /** Sin saltos de linea ni caracteres de control: un nombre no puede inyectar lineas en el texto del aviso. */
    private static String clean(String s) { return s == null ? "" : s.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replace('\n', ' ').trim(); }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
