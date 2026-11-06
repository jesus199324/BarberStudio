package com.barberstudio.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.shop.Barbershop;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Pruebas sin Spring ni base de datos: el aviso por email no puede romper ni duplicar reservas. */
class NotificationTest {
    static final ZoneId UY = ZoneId.of("America/Montevideo");

    private BookingNotification sample(boolean pending, String name) {
        Instant start = java.time.ZonedDateTime.of(2026, 11, 6, 19, 0, 0, 0, UY).toInstant();
        return new BookingNotification(7, "IKONOSTUDIO", name, "+59899111222", null, "https://wa.me/59899111222?text=Hola",
            "Corte", new BigDecimal("370.00"), 30, "Jesus", start, UY, pending, "Fade bajo", "https://sitio.example/#/panel");
    }

    @Test
    void pendingMessageHasAllTheDataAndSaysItIsNotConfirmed() {
        var m = NotificationMessage.build(sample(true, "Martín Pérez"));
        assertThat(m.subject()).contains("pendiente de aprobación").contains("Martín Pérez").contains("19:00");
        assertThat(m.text()).contains("NUEVA SOLICITUD DE TURNO").contains("Estado: PENDIENTE DE APROBACIÓN")
            .contains("- Nombre: Martín Pérez").contains("- Teléfono: +59899111222")
            .contains("- Servicio: Corte").contains("- Precio: $ 370").contains("- Duración: 30 min")
            .contains("- Fecha: Viernes 6 de noviembre de 2026").contains("- Hora: 19:00")
            .contains("todavía no está confirmado").contains("Requiere aprobación desde el panel administrativo")
            .contains("Panel: https://sitio.example/#/panel").contains("Fade bajo");
        assertThat(m.text()).doesNotContain("Email:");   // el sitio no pide email: se omite
        assertThat(m.html()).contains("PENDIENTE DE APROBACIÓN");
    }

    @Test
    void autoConfirmedMessageDoesNotClaimItIsPending() {
        var m = NotificationMessage.build(sample(false, "Ana"));
        assertThat(m.text()).contains("CONFIRMADO AUTOMÁTICAMENTE").doesNotContain("PENDIENTE").doesNotContain("todavía no está confirmado");
    }

    @Test
    void clientDataCannotInjectLinesOrHtml() {
        var m = NotificationMessage.build(sample(true, "Ana\nEstado: CONFIRMADO <script>x</script>"));
        assertThat(m.text()).doesNotContain("\nEstado: CONFIRMADO");
        assertThat(m.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    // ---------- cliente de Resend contra un servidor local ----------
    static class Fake {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final List<String> auth = new CopyOnWriteArrayList<>(), idem = new CopyOnWriteArrayList<>(), agents = new CopyOnWriteArrayList<>();
        final int[] statuses;

        Fake(int... statuses) throws Exception {
            this.statuses = statuses;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/emails", ex -> {
                int i = calls.getAndIncrement();
                bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                auth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
                idem.add(String.valueOf(ex.getRequestHeaders().getFirst("Idempotency-Key")));
                agents.add(String.valueOf(ex.getRequestHeaders().getFirst("User-Agent")));
                int st = statuses[Math.min(i, statuses.length - 1)];
                byte[] out = (st < 300 ? "{\"id\":\"abc\"}" : "{\"message\":\"error\"}").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(st, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/emails"; }
        void stop() { server.stop(0); }
    }

    private ResendClient client(Fake f) {
        return new ResendClient(f.url(), "re_test_key", "IKONOSTUDIO <onboarding@resend.dev>", "dueño@example.com, otro@example.com", Duration.ofSeconds(3), 10);
    }

    @Test
    void sendsOneAuthenticatedIdempotentRequest() throws Exception {
        var f = new Fake(200);
        try {
            client(f).send("Asunto \"con comillas\"", "línea 1\nlínea 2", "<b>hola</b>", "booking-7");
            assertThat(f.calls.get()).isEqualTo(1);
            assertThat(f.auth.get(0)).isEqualTo("Bearer re_test_key");
            assertThat(f.idem.get(0)).isEqualTo("booking-7");
            assertThat(f.agents.get(0)).contains("ikonostudio");
            assertThat(f.bodies.get(0)).contains("\"to\":[\"dueño@example.com\",\"otro@example.com\"]")
                .contains("\"subject\":\"Asunto \\\"con comillas\\\"\"").contains("línea 1\\nlínea 2");
        } finally { f.stop(); }
    }

    @Test
    void retriesOnceOnServerErrorAndGivesUpAfterwards() throws Exception {
        var ok = new Fake(500, 200);
        try { client(ok).send("a", "b", "c", "k"); assertThat(ok.calls.get()).isEqualTo(2); } finally { ok.stop(); }
        var bad = new Fake(500);
        try {
            assertThatThrownBy(() -> client(bad).send("a", "b", "c", "k")).hasMessageContaining("500");
            assertThat(bad.calls.get()).isEqualTo(2);
        } finally { bad.stop(); }
    }

    @Test
    void doesNotRetryOnClientErrors() throws Exception {
        var f = new Fake(403);
        try {
            assertThatThrownBy(() -> client(f).send("a", "b", "c", "k")).hasMessageContaining("403");
            assertThat(f.calls.get()).isEqualTo(1);
        } finally { f.stop(); }
    }

    @Test
    void isDisabledWithoutKeyOrRecipient() {
        assertThat(new ResendClient("http://x", "", "f", "a@b.c", Duration.ofSeconds(1), 1).enabled()).isFalse();
        assertThat(new ResendClient("http://x", "k", "f", " ", Duration.ofSeconds(1), 1).enabled()).isFalse();
        assertThat(new ResendClient("http://x", "k", "f", "a@b.c", Duration.ofSeconds(1), 1).enabled()).isTrue();
    }

    // ---------- el notificador nunca rompe el flujo de reservas ----------
    static class Recorder implements NotificationSender {
        final boolean enabled; final boolean fail; final List<String> keys = new ArrayList<>(), subjects = new ArrayList<>();
        Recorder(boolean enabled, boolean fail) { this.enabled = enabled; this.fail = fail; }
        public boolean enabled() { return enabled; }
        public void send(String subject, String text, String html, String key) throws Exception {
            keys.add(key); subjects.add(subject);
            if (fail) throw new java.io.IOException("proveedor caído");
        }
    }

    private Barbershop shop() {
        var s = new Barbershop();
        s.setName("IKONOSTUDIO_");
        s.setTimezone("America/Montevideo");
        return s;
    }

    private AppointmentResponse appt() {
        Instant start = java.time.ZonedDateTime.of(2026, 11, 6, 19, 0, 0, 0, UY).toInstant();
        return new AppointmentResponse(42L, 1L, "Martín Pérez", "+59899111222", 3L, "Corte", start, start.plusSeconds(1800),
            30, new BigDecimal("370"), AppointmentStatus.PENDING, "Reserva desde la web.", 5L, "Jesus");
    }

    @Test
    void notifierSendsOnceWithBookingKeyAndNeverThrowsWhenProviderFails() {
        var ok = new Recorder(true, false);
        new BookingNotifier(ok, "").newBooking(shop(), appt(), "+59899111222", null);
        assertThat(ok.keys).containsExactly("booking-42");
        assertThat(ok.subjects.get(0)).contains("IKONOSTUDIO").doesNotContain("IKONOSTUDIO_").contains("pendiente de aprobación");

        var down = new Recorder(true, true);
        assertThatCode(() -> new BookingNotifier(down, "").newBooking(shop(), appt(), "+59899111222", null)).doesNotThrowAnyException();
        assertThat(down.keys).hasSize(1);
    }

    @Test
    void notifierDoesNothingWhenNotConfigured() {
        var off = new Recorder(false, false);
        assertThatCode(() -> new BookingNotifier(off, "").newBooking(shop(), appt(), "+59899111222", null)).doesNotThrowAnyException();
        assertThat(off.keys).isEmpty();
    }
}
