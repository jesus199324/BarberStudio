package com.barberstudio.common.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario demo SOLO en el perfil "dev" (nunca en produccion).
 * La contrasena se hashea con BCrypt en tiempo de ejecucion.
 */
@Component
@Profile("dev")
public class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);
    static final String DEMO_EMAIL = "demo@barberstudio.test";
    static final String DEMO_PASSWORD = "demo1234";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DemoUserInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer exists = jdbc.queryForObject(
            "SELECT count(*) FROM app_user WHERE lower(email) = ?", Integer.class, DEMO_EMAIL);
        Long shopId = jdbc.query("SELECT id FROM barbershop WHERE slug = 'barber-studio-demo'",
            rs -> rs.next() ? rs.getLong(1) : null);
        if (exists != null && exists == 0 && shopId != null) {
            jdbc.update("INSERT INTO app_user (barbershop_id, email, password_hash, role) VALUES (?, ?, ?, 'OWNER')",
                shopId, DEMO_EMAIL, encoder.encode(DEMO_PASSWORD));
            log.info("Usuario demo creado: {} (solo desarrollo)", DEMO_EMAIL);
        }
    }
}
