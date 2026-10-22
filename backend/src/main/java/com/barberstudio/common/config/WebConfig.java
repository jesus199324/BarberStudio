package com.barberstudio.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve las imagenes del almacenamiento local (solo desarrollo / instancias con disco persistente). */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final Path root;

    public WebConfig(@Value("${app.storage.local-dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(root.toUri().toString())
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
