package com.barberstudio.common.dev;

import com.barberstudio.common.util.Slugs;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Produccion: si la base esta vacia, crea TU barberia, TU usuario dueño y TU barbero con las variables BOOTSTRAP_*.
 * No crea datos de demostracion ni horarios: la disponibilidad empieza vacia hasta que cargues tus horarios en el panel.
 */
@Component
@Profile("prod")
public class BootstrapInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);
    private static final List<String[]> STYLES = List.of(
        new String[]{"Taper fade", "CORTE"}, new String[]{"Low fade", "CORTE"}, new String[]{"Mid fade", "CORTE"},
        new String[]{"High fade", "CORTE"}, new String[]{"Mohicano", "CORTE"}, new String[]{"Corte clásico", "CORTE"},
        new String[]{"Crop", "CORTE"}, new String[]{"Cortes con rulos", "CORTE"}, new String[]{"Cortes infantiles", "CORTE"},
        new String[]{"Mechas", "COLOR"}, new String[]{"Decoloración", "COLOR"}, new String[]{"Color fantasía", "COLOR"},
        new String[]{"Barba perfilada", "BARBA"}, new String[]{"Barba full", "BARBA"},
        new String[]{"Lettering", "TATUAJE"}, new String[]{"Línea fina", "TATUAJE"});

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String name, slug, whatsapp, email, password, barberName;

    public BootstrapInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${app.bootstrap.shop-name:}") String name, @Value("${app.bootstrap.shop-slug:}") String slug,
            @Value("${app.bootstrap.whatsapp:}") String whatsapp, @Value("${app.bootstrap.owner-email:}") String email,
            @Value("${app.bootstrap.owner-password:}") String password, @Value("${app.bootstrap.barber-name:}") String barberName) {
        this.jdbc = jdbc; this.encoder = encoder; this.name = name; this.slug = slug;
        this.whatsapp = whatsapp; this.email = email; this.password = password; this.barberName = barberName;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer shops = jdbc.queryForObject("SELECT count(*) FROM barbershop", Integer.class);
        if (shops == null || shops > 0) return;
        if (name.isBlank() || email.isBlank() || password.length() < 10 || !whatsapp.matches("^\\+[1-9][0-9]{7,14}$")) {
            log.warn("Base vacia y faltan datos BOOTSTRAP_* validos (nombre, email, WhatsApp +598..., clave de 10+ caracteres). No se creo nada.");
            return;
        }
        String finalSlug = slug.isBlank() ? Slugs.slugify(name) : Slugs.slugify(slug);
        Long shopId = jdbc.queryForObject("INSERT INTO barbershop (name, slug, whatsapp_e164) VALUES (?, ?, ?) RETURNING id",
            Long.class, name.trim(), finalSlug, whatsapp);
        jdbc.update("INSERT INTO app_user (barbershop_id, email, password_hash, role) VALUES (?, ?, ?, 'OWNER')",
            shopId, email.trim().toLowerCase(), encoder.encode(password));
        jdbc.update("INSERT INTO barber (barbershop_id, name) VALUES (?, ?)", shopId, barberName.isBlank() ? name.trim() : barberName.trim());
        for (int i = 0; i < STYLES.size(); i++) {
            jdbc.update("INSERT INTO haircut_style (barbershop_id, name, slug, category, sort_order) VALUES (?, ?, ?, ?, ?)",
                shopId, STYLES.get(i)[0], Slugs.slugify(STYLES.get(i)[0]), STYLES.get(i)[1], i + 1);
        }
        log.info("Barberia '{}' creada (slug: {}). Ya podes iniciar sesion con {}.", name, finalSlug, email);
    }
}
