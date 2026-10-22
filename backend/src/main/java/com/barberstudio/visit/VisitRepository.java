package com.barberstudio.visit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    Optional<Visit> findByAppointmentIdAndBarbershopId(Long appointmentId, Long barbershopId);
    Optional<Visit> findByAppointmentId(Long appointmentId);
    List<Visit> findByClientIdAndBarbershopIdOrderByPerformedAtDesc(Long clientId, Long barbershopId);

    /** Ingresos registrados = suma del precio final de visitas realizadas (nunca de turnos futuros o cancelados). */
    @Query("select coalesce(sum(v.finalPrice), 0) from Visit v "
         + "where v.barbershopId = :shop and v.performedAt >= :from and v.performedAt < :to")
    BigDecimal incomeBetween(@Param("shop") Long shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(v) from Visit v where v.barbershopId = :shop and v.performedAt >= :from and v.performedAt < :to")
    long countBetween(@Param("shop") Long shop, @Param("from") Instant from, @Param("to") Instant to);
}
