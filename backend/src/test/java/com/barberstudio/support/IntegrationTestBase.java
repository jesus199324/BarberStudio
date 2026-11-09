package com.barberstudio.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base para pruebas de integracion contra PostgreSQL real (requiere Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static { PG.start(); }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }

    protected static final String PASSWORD = "secret-pass-1";
    protected static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder encoder;

    protected void truncateAll() {
        jdbc.execute("TRUNCATE barbershop RESTART IDENTITY CASCADE");
    }

    protected long seedShop(String slug) {
        return jdbc.queryForObject(
            "INSERT INTO barbershop (name, slug, whatsapp_e164) VALUES (?,?,'+59899000000') RETURNING id",
            Long.class, slug, slug);
    }

    protected long seedUser(long shopId, String email, String role) {
        return jdbc.queryForObject(
            "INSERT INTO app_user (barbershop_id, email, password_hash, role) VALUES (?,?,?,?) RETURNING id",
            Long.class, shopId, email, encoder.encode(PASSWORD), role);
    }

    protected Cookie login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("access_token");
    }

    protected MvcResult postJson(Cookie c, String url, String body) throws Exception {
        return mvc.perform(post(url).cookie(c).contentType(JSON).content(body))
            .andExpect(status().is2xxSuccessful()).andReturn();
    }

    protected long id(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
