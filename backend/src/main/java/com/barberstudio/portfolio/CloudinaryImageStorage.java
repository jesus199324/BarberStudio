package com.barberstudio.portfolio;

import com.barberstudio.common.error.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Guarda las fotos en Cloudinary (plan gratuito) con subida firmada: el secreto nunca sale del servidor.
 * Se activa con STORAGE_PROVIDER=cloudinary y las variables CLOUDINARY_*.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryImageStorage implements ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);
    private static final String BASE = "https://api.cloudinary.com/v1_1/";

    private final String cloud, apiKey, apiSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public CloudinaryImageStorage(@Value("${app.cloudinary.cloud-name:}") String cloud,
                                  @Value("${app.cloudinary.api-key:}") String apiKey,
                                  @Value("${app.cloudinary.api-secret:}") String apiSecret) {
        if (cloud.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("Faltan CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY o CLOUDINARY_API_SECRET.");
        }
        this.cloud = cloud.trim();
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
    }

    @Override
    public StoredImage store(long shopId, byte[] bytes, String extension) {
        String publicId = "barber-studio/shop-" + shopId + "/" + UUID.randomUUID();
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String signature = sha1("public_id=" + publicId + "&timestamp=" + ts + apiSecret);   // parametros en orden alfabetico + secreto
        String boundary = "----barber" + UUID.randomUUID();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            field(out, boundary, "api_key", apiKey);
            field(out, boundary, "timestamp", ts);
            field(out, boundary, "public_id", publicId);
            field(out, boundary, "signature", signature);
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload." + extension
                + "\"\r\nContent-Type: " + mime(extension) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + cloud + "/image/upload")).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.error("Cloudinary respondio {}: {}", res.statusCode(), res.body());
                throw new BusinessRuleException("No se pudo guardar la imagen. Probá de nuevo en un momento.");
            }
            JsonNode json = mapper.readTree(res.body());
            return new StoredImage(json.get("public_id").asText(), json.get("secure_url").asText());
        } catch (IOException e) {
            log.error("Error subiendo a Cloudinary", e);
            throw new BusinessRuleException("No se pudo guardar la imagen. Probá de nuevo en un momento.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("La subida de la imagen fue interrumpida.");
        }
    }

    @Override
    public void delete(String key) {
        try {
            String ts = String.valueOf(Instant.now().getEpochSecond());
            String body = "public_id=" + enc(key) + "&timestamp=" + ts + "&api_key=" + enc(apiKey)
                + "&signature=" + sha1("public_id=" + key + "&timestamp=" + ts + apiSecret);
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + cloud + "/image/destroy")).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) log.warn("No se pudo borrar {} en Cloudinary: {}", key, res.body());
        } catch (IOException e) {
            log.warn("No se pudo borrar {} en Cloudinary", key, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String mime(String ext) {
        return switch (ext) { case "jpg" -> "image/jpeg"; case "png" -> "image/png"; default -> "image/webp"; };
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
