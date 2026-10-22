package com.barberstudio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/** Flujos criticos de la API contra PostgreSQL real. Requiere Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiFlowIntegrationTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static { PG.start(); }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }

    static final String PASSWORD = "secret-pass-1";
    static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    long clientId, serviceId, barberId;
    Cookie owner;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE barbershop RESTART IDENTITY CASCADE");
        seedShop("shop-a", "a@test.com");
        seedShop("shop-b", "b@test.com");
        owner = login("a@test.com");
        clientId = id(postJson("/api/clients", "{\"name\":\"Cliente Uno\",\"whatsapp\":\"099 111 222\"}"));
        barberId = id(postJson("/api/barbers", "{\"name\":\"Barbero Uno\"}"));
        serviceId = id(postJson("/api/services", "{\"name\":\"Corte\",\"price\":600,\"durationMinutes\":30}"));
    }

    @Test
    void privateEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON)
            .content("{\"email\":\"a@test.com\",\"password\":\"incorrecta\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateClientPhoneIsRejectedAndPhoneIsNormalized() throws Exception {
        mvc.perform(post("/api/clients").cookie(owner).contentType(JSON)
            .content("{\"name\":\"Otro\",\"whatsapp\":\"+598 99 111 222\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.existingClientId").value(clientId));
        mvc.perform(get("/api/clients/" + clientId).cookie(owner))
            .andExpect(jsonPath("$.whatsapp").value("+59899111222"));
    }

    @Test
    void overlappingAppointmentsAreRejectedButBackToBackAreAllowed() throws Exception {
        appointment("2031-03-03T13:00:00Z");
        mvc.perform(post("/api/appointments").cookie(owner).contentType(JSON).content(appointmentBody("2031-03-03T13:15:00Z")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/appointments").cookie(owner).contentType(JSON).content(appointmentBody("2031-03-03T13:30:00Z")))
            .andExpect(status().isCreated());
    }

    @Test
    void completingCreatesOneVisitAndIsIdempotent() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long appt = id(appointment(now.toString()));
        long v1 = id(postJson("/api/appointments/" + appt + "/complete", "{}"));
        long v2 = id(postJson("/api/appointments/" + appt + "/complete", "{}"));
        assertThat(v2).isEqualTo(v1);
        assertThat(jdbc.queryForObject("select count(*) from visit", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/dashboard/summary").cookie(owner))
            .andExpect(jsonPath("$.registeredIncomeToday").value(600))
            .andExpect(jsonPath("$.completedToday").value(1));
    }

    @Test
    void cancelledAppointmentCannotBeCompletedAndGeneratesNoIncome() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long appt = id(appointment(now.toString()));
        mvc.perform(post("/api/appointments/" + appt + "/cancel").cookie(owner)).andExpect(status().isOk());
        mvc.perform(post("/api/appointments/" + appt + "/complete").cookie(owner).contentType(JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from visit", Integer.class)).isZero();
        mvc.perform(get("/api/dashboard/summary").cookie(owner)).andExpect(jsonPath("$.registeredIncomeToday").value(0));
    }

    @Test
    void anotherShopCannotSeeMyData() throws Exception {
        Cookie other = login("b@test.com");
        mvc.perform(get("/api/clients/" + clientId).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clients").cookie(other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void draftPortfolioIsHiddenUntilPublishedAndImagesAreValidated() throws Exception {
        long style = id(postJson("/api/styles", "{\"name\":\"Taper fade\"}"));
        long item = id(postJson("/api/portfolio", "{\"styleId\":" + style + ",\"title\":\"Mi taper\",\"tags\":[\"Fade\"]}"));
        mvc.perform(get("/api/public/shop-a/portfolio")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/public/shop-a/portfolio/" + item)).andExpect(status().isNotFound());

        mvc.perform(post("/api/portfolio/" + item + "/publish").cookie(owner)).andExpect(status().isUnprocessableEntity()); // sin imagen

        MockMultipartFile fake = new MockMultipartFile("file", "x.png", "image/png", "no soy una imagen".getBytes());
        mvc.perform(multipart("/api/portfolio/" + item + "/images").file(fake).cookie(owner)).andExpect(status().isUnprocessableEntity());

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mvc.perform(multipart("/api/portfolio/" + item + "/images")
            .file(new MockMultipartFile("file", "a.png", "image/png", png)).param("alt", "Vista lateral").cookie(owner))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/portfolio/" + item + "/publish").cookie(owner)).andExpect(status().isOk());

        mvc.perform(get("/api/public/shop-a/portfolio?style=taper-fade")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/public/shop-a/portfolio/" + item))
            .andExpect(jsonPath("$.whatsappUrl").value(org.hamcrest.Matchers.startsWith("https://wa.me/")));
    }

    @Test
    void publicRequestIsNotAnAppointment() throws Exception {
        mvc.perform(post("/api/public/shop-a/requests").contentType(JSON)
            .content("{\"name\":\"Juan\",\"whatsapp\":\"099 123 456\",\"message\":\"Hola\"}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from appointment_request where status = 'NEW'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isZero();
    }

    @Test
    void honeypotRequestsAreNotStored() throws Exception {
        mvc.perform(post("/api/public/shop-a/requests").contentType(JSON)
            .content("{\"name\":\"Bot\",\"whatsapp\":\"099 123 457\",\"website\":\"http://spam\"}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from appointment_request", Integer.class)).isZero();
    }

    // ---------- disponibilidad, reservas y cierres ----------
    static final ZoneId ZONE = ZoneId.of("America/Montevideo");

    @Test
    void slotsDependOnRealServiceDurationAndBookedIntervals() throws Exception {
        LocalDate d = workDay();
        setHours(barberId, d, "10:00", "13:00");
        long long45 = id(postJson("/api/services", "{\"name\":\"Corte largo\",\"price\":900,\"durationMinutes\":45}"));
        String url = "/api/public/shop-a/availability/slots?serviceId=" + long45 + "&barberId=" + barberId + "&date=" + d;
        mvc.perform(get(url)).andExpect(jsonPath("$[0].label").value("10:00"))
            .andExpect(jsonPath("$[?(@.label=='12:15')]").isNotEmpty())
            .andExpect(jsonPath("$[?(@.label=='12:30')]").isEmpty());   // 12:30 + 45 min pasaria de las 13:00
        mvc.perform(post("/api/public/shop-a/bookings").contentType(JSON)
            .content("{\"serviceId\":" + long45 + ",\"barberId\":" + barberId + ",\"startAt\":\"" + at(d, 10, 0)
                + "\",\"name\":\"Ana\",\"whatsapp\":\"099 222 333\"}")).andExpect(status().isCreated());
        mvc.perform(get(url)).andExpect(jsonPath("$[?(@.label=='10:30')]").isEmpty())    // 10:00 a 10:45 ocupado
            .andExpect(jsonPath("$[?(@.label=='10:45')]").isNotEmpty());
    }

    @Test
    void generalClosureBlocksEveryoneAndOneBarberLeaveDoesNot() throws Exception {
        LocalDate d = workDay();
        setHours(barberId, d, "10:00", "13:00");
        long b2 = id(postJson("/api/barbers", "{\"name\":\"Barbero Dos\"}"));
        setHours(b2, d, "10:00", "13:00");
        postJson("/api/closures", "{\"barberId\":" + barberId + ",\"startDate\":\"" + d + "\",\"endDate\":\"" + d + "\",\"reason\":\"LICENCIA\"}");
        String any = "/api/public/shop-a/availability/slots?serviceId=" + serviceId + "&date=" + d;
        mvc.perform(get(any)).andExpect(jsonPath("$[0].barberIds.length()").value(1)).andExpect(jsonPath("$[0].barberIds[0]").value(b2));
        postJson("/api/closures", "{\"startDate\":\"" + d + "\",\"endDate\":\"" + d + "\",\"reason\":\"FERIADO\"}");
        mvc.perform(get(any)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/public/shop-a/availability/days?serviceId=" + serviceId + "&from=" + d + "&to=" + d))
            .andExpect(jsonPath("$[0].status").value("CLOSED")).andExpect(jsonPath("$[0].reason").value("Feriado"));
    }

    @Test
    void closureAffectingBookedAppointmentsNeedsAuthorizationAndNeverCancelsByItself() throws Exception {
        LocalDate d = workDay();
        long appt = id(appointment(at(d, 11, 0)));
        String body = "{\"startDate\":\"" + d + "\",\"endDate\":\"" + d + "\",\"reason\":\"MANTENIMIENTO\"%s}";
        mvc.perform(post("/api/closures").cookie(owner).contentType(JSON).content(String.format(body, "")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.affected.length()").value(1));
        mvc.perform(post("/api/closures").cookie(owner).contentType(JSON).content(String.format(body, ",\"authorizeAffected\":true")))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/appointments/" + appt).cookie(owner)).andExpect(jsonPath("$.status").value("CONFIRMED"));   // sigue en pie
    }

    @Test
    void anyBarberBookingUsesTheSecondBarberAndRejectsWhenBothAreTaken() throws Exception {
        LocalDate d = workDay();
        setHours(barberId, d, "10:00", "13:00");
        long b2 = id(postJson("/api/barbers", "{\"name\":\"Barbero Dos\"}"));
        setHours(b2, d, "10:00", "13:00");
        String body = "{\"serviceId\":" + serviceId + ",\"startAt\":\"" + at(d, 10, 0) + "\",\"name\":\"%s\",\"whatsapp\":\"%s\"}";
        mvc.perform(post("/api/public/shop-a/bookings").contentType(JSON).content(String.format(body, "Ana", "099 222 333"))).andExpect(status().isCreated());
        mvc.perform(post("/api/public/shop-a/bookings").contentType(JSON).content(String.format(body, "Beto", "099 333 444"))).andExpect(status().isCreated());
        mvc.perform(post("/api/public/shop-a/bookings").contentType(JSON).content(String.format(body, "Carla", "099 444 555"))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(distinct barber_id) from appointment", Integer.class)).isEqualTo(2);
    }

    @Test
    void stylesHaveCategoriesAndPortfolioItemsCanBeDeleted() throws Exception {
        long style = id(postJson("/api/styles", "{\"name\":\"Barba full\",\"category\":\"BARBA\"}"));
        mvc.perform(get("/api/public/shop-a/styles")).andExpect(jsonPath("$[0].category").value("BARBA"));
        long item = id(postJson("/api/portfolio", "{\"styleId\":" + style + ",\"title\":\"Barba\"}"));
        mvc.perform(delete("/api/portfolio/" + item).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/portfolio/" + item).cookie(owner)).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------
    private LocalDate workDay() { return LocalDate.now(ZONE).plusDays(7); }

    private String at(LocalDate d, int h, int m) { return d.atTime(h, m).atZone(ZONE).toInstant().toString(); }

    private void setHours(long barber, LocalDate d, String start, String end) throws Exception {
        mvc.perform(put("/api/barbers/" + barber + "/hours").cookie(owner).contentType(JSON)
            .content("{\"items\":[{\"weekday\":" + d.getDayOfWeek().getValue() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}]}"))
            .andExpect(status().isOk());
    }

    private void seedShop(String slug, String email) {
        Long shop = jdbc.queryForObject("INSERT INTO barbershop (name, slug, whatsapp_e164) VALUES (?,?,'+59899000000') RETURNING id",
            Long.class, slug, slug);
        jdbc.update("INSERT INTO app_user (barbershop_id, email, password_hash, role) VALUES (?,?,?,'OWNER')",
            shop, email, encoder.encode(PASSWORD));
    }

    private Cookie login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("access_token");
    }

    private MvcResult postJson(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
            .cookie(owner).contentType(JSON).content(body)).andExpect(status().is2xxSuccessful()).andReturn();
    }

    private String appointmentBody(String start) {
        return "{\"clientId\":" + clientId + ",\"serviceId\":" + serviceId + ",\"barberId\":" + barberId + ",\"startAt\":\"" + start + "\"}";
    }

    private MvcResult appointment(String start) throws Exception { return postJson("/api/appointments", appointmentBody(start)); }

    private long id(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
