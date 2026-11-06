package com.barberstudio.notification;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Configuracion de los avisos. Las claves viven en variables de entorno (nunca en el frontend ni en el repositorio). */
@Configuration
@EnableAsync
public class NotificationConfig {
    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    NotificationSender notificationSender(
            @Value("${app.notifications.resend-api-key:}") String apiKey,
            @Value("${app.notifications.to:}") String to,
            @Value("${app.notifications.from:IKONOSTUDIO <onboarding@resend.dev>}") String from) {
        var client = new ResendClient("https://api.resend.com/emails", apiKey, from, to, Duration.ofSeconds(8), 1500);
        if (client.enabled()) log.info("Avisos de nuevas reservas por email: ACTIVADOS.");
        else log.info("Avisos de nuevas reservas por email: desactivados (faltan RESEND_API_KEY o NOTIFY_EMAIL_TO).");
        return client;
    }

    /** Hilo aparte y acotado: un proveedor lento no frena las reservas ni acumula hilos sin limite. */
    @Bean(name = "notificationExecutor")
    ThreadPoolTaskExecutor notificationExecutor() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("notify-");
        ex.setRejectedExecutionHandler((r, e) -> log.error("Cola de avisos llena: se descartó un aviso (la reserva sí se guardó)."));
        return ex;
    }
}
