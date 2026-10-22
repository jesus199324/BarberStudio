package com.barberstudio.barber;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberRepository extends JpaRepository<Barber, Long> {
    Optional<Barber> findByIdAndBarbershopId(Long id, Long barbershopId);
    List<Barber> findByBarbershopIdOrderBySortOrderAscNameAsc(Long barbershopId);
    List<Barber> findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(Long barbershopId);
}
