package com.barberstudio.portfolio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalDiskImageStorage implements ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalDiskImageStorage.class);
    private final Path root;
    private final String baseUrl;

    public LocalDiskImageStorage(@Value("${app.storage.local-dir}") String dir,
                                 @Value("${app.storage.public-base-url:}") String baseUrl) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    @Override
    public StoredImage store(long shopId, byte[] bytes, String extension) {
        String key = shopId + "/" + UUID.randomUUID() + "." + extension;   // nombre generado: nunca se usa el del cliente
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Ruta inválida");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        return new StoredImage(key, baseUrl + "/uploads/" + key);
    }

    @Override
    public void delete(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("No se pudo borrar {}", key, e);
        }
    }
}
