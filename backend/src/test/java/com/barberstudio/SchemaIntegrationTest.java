package com.barberstudio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica contra un PostgreSQL real que las reglas de integridad del esquema
 * (V1__init_schema.sql) se cumplen. Requiere Docker en ejecucion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SchemaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbc;

    long shopId, clientId, serviceId, barberId;
    final OffsetDateTime t10 = OffsetDateTime.of(2030, 1, 7, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE barbershop RESTART IDENTITY CASCADE");
        shopId = id("INSERT INTO barbershop (name, slug, whatsapp_e164) VALUES ('T','t','+59899000000') RETURNING id");
        clientId = client("+59899111111");
        barberId = id("INSERT INTO barber (barbershop_id, name) VALUES (" + shopId + ",'B1') RETURNING id");
        serviceId = id("INSERT INTO service (barbershop_id, name, price, duration_minutes) "
                + "VALUES (" + shopId + ",'Corte',600,30) RETURNING id");
    }

    @Test
    void rejectsOverlappingAppointments() {
        appointment(t10, t10.plusMinutes(30), "CONFIRMED");
        assertThatThrownBy(() -> appointment(t10.plusMinutes(15), t10.plusMinutes(45), "PENDING"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsBackToBackAppointments() {
        appointment(t10, t10.plusMinutes(30), "CONFIRMED");
        appointment(t10.plusMinutes(30), t10.plusMinutes(60), "CONFIRMED"); // no debe fallar
    }

    @Test
    void cancelledAppointmentsDoNotBlockTheSlot() {
        appointment(t10, t10.plusMinutes(30), "CANCELLED");
        appointment(t10, t10.plusMinutes(30), "CONFIRMED"); // no debe fallar
    }

    @Test
    void rejectsSecondVisitForSameAppointment() {
        long appt = appointment(t10, t10.plusMinutes(30), "COMPLETED");
        insertVisit(appt);
        assertThatThrownBy(() -> insertVisit(appt)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateActiveClientPhoneButAllowsAfterArchiving() {
        assertThatThrownBy(() -> client("+59899111111")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE client SET archived_at = now() WHERE id = ?", clientId);
        assertThat(client("+59899111111")).isNotEqualTo(clientId);
    }

    @Test
    void rejectsInvalidPhoneFormat() {
        assertThatThrownBy(() -> client("099 111 111")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPublishedItemWithoutPublicationDate() {
        long style = id("INSERT INTO haircut_style (barbershop_id, name, slug) VALUES ("
                + shopId + ",'Taper','taper') RETURNING id");
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO portfolio_item (barbershop_id, style_id, title, status) VALUES (?,?,?,'PUBLISHED')",
            shopId, style, "Sin fecha")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentBarbersCanHaveTheSameSlot() {
        long other = id("INSERT INTO barber (barbershop_id, name) VALUES (" + shopId + ",'B2') RETURNING id");
        appointmentFor(barberId, t10, t10.plusMinutes(30), "CONFIRMED");
        appointmentFor(other, t10, t10.plusMinutes(30), "CONFIRMED");   // otro barbero: no debe fallar
    }

    // ---- helpers ----
    private long id(String sql) { return jdbc.queryForObject(sql, Long.class); }

    private long client(String phone) {
        return jdbc.queryForObject("INSERT INTO client (barbershop_id, name, whatsapp_e164) VALUES (?,?,?) RETURNING id",
            Long.class, shopId, "Cliente", phone);
    }

    private long appointment(OffsetDateTime start, OffsetDateTime end, String status) {
        return appointmentFor(barberId, start, end, status);
    }

    private long appointmentFor(long barber, OffsetDateTime start, OffsetDateTime end, String status) {
        return jdbc.queryForObject(
            "INSERT INTO appointment (barbershop_id, client_id, service_id, barber_id, start_at, end_at, "
          + "price_agreed, duration_minutes, status) VALUES (?,?,?,?,?,?,600,30,?) RETURNING id",
            Long.class, shopId, clientId, serviceId, barber, start, end, status);
    }

    private void insertVisit(long appointmentId) {
        jdbc.update("INSERT INTO visit (barbershop_id, appointment_id, client_id, performed_at, service_name, final_price) "
                  + "VALUES (?,?,?,?, 'Corte', 600)", shopId, appointmentId, clientId, t10);
    }
}
