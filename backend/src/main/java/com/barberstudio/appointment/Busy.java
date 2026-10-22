package com.barberstudio.appointment;

import java.time.Instant;

/** Intervalo ocupado por un turno (proyeccion liviana para calcular disponibilidad). */
public record Busy(Long barberId, Instant startAt, Instant endAt) {}
