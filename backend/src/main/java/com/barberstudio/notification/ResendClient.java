package com.barberstudio.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Cliente minimo de la API de Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Usa solo el JDK: no agrega dependencias. Reintenta una vez ante errores de red o 5xx/429.
 */
public class ResendClient implements NotificationSender {
    private final HttpClient http;
    private final String endpoint, apiKey, from;
    private final List<String> to;
    private final Duration timeout;
    private final long retryDelayMillis;

    public ResendClient(String endpoint, String apiKey, String from, String toCsv, Duration timeout, long retryDelayMillis) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from;
        this.to = toCsv == null ? List.of() : Arrays.stream(toCsv.split("[,;]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.timeout = timeout;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public boolean enabled() { return !apiKey.isEmpty() && !to.isEmpty(); }

    @Override
    public void send(String subject, String text, String html, String idempotencyKey) throws Exception {
        String body = "{\"from\":" + json(from) + ",\"to\":[" + String.join(",", to.stream().map(ResendClient::json).toList()) + "],"
            + "\"subject\":" + json(subject) + ",\"text\":" + json(text) + ",\"html\":" + json(html) + "}";
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("User-Agent", "ikonostudio-backend/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) rb.header("Idempotency-Key", idempotencyKey);
        HttpRequest req = rb.build();

        IOException lastIo = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int s = res.statusCode();
                if (s >= 200 && s < 300) return;
                String detail = "Resend respondió HTTP " + s + ": " + abbreviate(res.body());
                if (s == 429 || s >= 500) {
                    if (attempt == 2) throw new IOException(detail);
                } else {
                    throw new IllegalStateException(detail);   // 4xx (clave, remitente o destinatario no validos): reintentar no sirve
                }
            } catch (IOException e) {
                lastIo = e;
                if (attempt == 2) throw e;
            }
            Thread.sleep(retryDelayMillis);
        }
        throw lastIo != null ? lastIo : new IOException("No se pudo enviar el aviso.");
    }

    static String abbreviate(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }

    /** Cadena JSON escapada (sin dependencias). */
    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
}
