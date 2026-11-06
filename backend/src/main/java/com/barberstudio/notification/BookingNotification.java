package com.barberstudio.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

/** Datos de una reserva recien guardada, listos para armar el aviso al dueño. Sin logica ni acceso a la base. */
public record BookingNotification(
    long appointmentId,
    String shopName,
    String clientName,
    String clientPhone,
    String clientEmail,          // opcional: hoy el sitio no lo pide, se omite si es null
    String clientWhatsappUrl,    // enlace wa.me para responderle (opcional)
    String serviceName,
    BigDecimal price,
    int durationMinutes,
    String barberName,
    Instant startAt,
    ZoneId zone,
    boolean pendingApproval,     // true = el dueño todavia tiene que aprobarla
    String clientComment,        // comentario / referencia del portfolio (opcional)
    String panelUrl) {}          // enlace al panel (opcional)
