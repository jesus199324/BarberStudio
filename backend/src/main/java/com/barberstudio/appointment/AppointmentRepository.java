package com.barberstudio.appointment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("select a from Appointment a join fetch a.client join fetch a.service join fetch a.barber "
         + "where a.barbershopId = :shop and a.startAt >= :from and a.startAt < :to order by a.startAt")
    List<Appointment> findInRange(@Param("shop") Long shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select a from Appointment a join fetch a.client join fetch a.service join fetch a.barber "
         + "where a.barbershopId = :shop and a.barber.id = :barberId and a.startAt >= :from and a.startAt < :to order by a.startAt")
    List<Appointment> findInRangeByBarber(@Param("shop") Long shop, @Param("barberId") Long barberId,
                                          @Param("from") Instant from, @Param("to") Instant to);

    /** Turnos abiertos (pendientes o confirmados) que se superponen con un rango: sirve para avisar de cierres. */
    @Query("select a from Appointment a join fetch a.client join fetch a.service join fetch a.barber "
         + "where a.barbershopId = :shop and a.status in (com.barberstudio.appointment.AppointmentStatus.PENDING, com.barberstudio.appointment.AppointmentStatus.CONFIRMED) "
         + "and a.startAt < :to and a.endAt > :from order by a.startAt")
    List<Appointment> findOpenInRange(@Param("shop") Long shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select a from Appointment a join fetch a.client join fetch a.service join fetch a.barber "
         + "where a.id = :id and a.barbershopId = :shop")
    Optional<Appointment> findDetail(@Param("id") Long id, @Param("shop") Long shop);

    /** Bloquea la fila: dos "completar" simultaneos se serializan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id and a.barbershopId = :shop")
    Optional<Appointment> lockById(@Param("id") Long id, @Param("shop") Long shop);

    @Query("select count(a) > 0 from Appointment a where a.barber.id = :barberId "
         + "and a.status <> com.barberstudio.appointment.AppointmentStatus.CANCELLED "
         + "and a.startAt < :end and a.endAt > :start and a.id <> :excludeId")
    boolean existsOverlap(@Param("barberId") Long barberId, @Param("start") Instant start,
                          @Param("end") Instant end, @Param("excludeId") Long excludeId);

    @Query("select new com.barberstudio.appointment.Busy(a.barber.id, a.startAt, a.endAt) from Appointment a "
         + "where a.barbershopId = :shop and a.status <> com.barberstudio.appointment.AppointmentStatus.CANCELLED "
         + "and a.startAt < :to and a.endAt > :from")
    List<Busy> busyBetween(@Param("shop") Long shop, @Param("from") Instant from, @Param("to") Instant to);

    long countByBarbershopIdAndStatusAndEndAtAfter(Long shop, AppointmentStatus status, Instant after);
    long countByBarberId(Long barberId);
    long countByBarberIdAndStartAtAfterAndStatusIn(Long barberId, Instant after, Collection<AppointmentStatus> statuses);
    long countByClientIdAndStartAtAfterAndStatusIn(Long clientId, Instant after, Collection<AppointmentStatus> statuses);
}
