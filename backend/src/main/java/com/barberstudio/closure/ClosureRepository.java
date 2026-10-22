package com.barberstudio.closure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosureRepository extends JpaRepository<Closure, Long> {
    Optional<Closure> findByIdAndBarbershopId(Long id, Long barbershopId);

    List<Closure> findByBarbershopIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDate(
        Long barbershopId, LocalDate from, LocalDate to);
}
