package com.barberstudio.common.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limitador de ventana fija en memoria, por clave (ej. IP). Limitacion conocida: el contador
 * es por instancia; con varias instancias habria que moverlo a Redis o a la base de datos.
 */
@Component
public class RateLimiter {
    private static final class Window {
        final long start;
        int count = 1;
        Window(long start) { this.start = start; }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;

    /** app.rate-limit.enabled=false lo apaga (solo se usa en el perfil de pruebas). */
    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean allow(String key, int max, Duration period) {
        if (!enabled) return true;
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) windows.clear();
        boolean[] ok = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.start >= period.toMillis()) { ok[0] = true; return new Window(now); }
            if (w.count < max) { w.count++; ok[0] = true; }
            return w;
        });
        return ok[0];
    }
}
