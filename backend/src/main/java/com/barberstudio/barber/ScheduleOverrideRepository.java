package com.barberstudio.barber;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleOverrideRepository extends JpaRepository<ScheduleOverride, Long> {
    List<ScheduleOverride> findByBarberIdOrderByWorkDate(Long barberId);
    List<ScheduleOverride> findByBarberIdInAndWorkDateBetween(Collection<Long> barberIds, LocalDate from, LocalDate to);
    Optional<ScheduleOverride> findByIdAndBarberId(Long id, Long barberId);
    Optional<ScheduleOverride> findByBarberIdAndWorkDate(Long barberId, LocalDate workDate);
}
