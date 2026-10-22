package com.barberstudio.common.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
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

    public boolean allow(String key, int max, Duration period) {
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
