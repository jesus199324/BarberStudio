package com.barberstudio.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberServiceRepository extends JpaRepository<BarberService, Long> {
    Optional<BarberService> findByIdAndBarbershopId(Long id, Long barbershopId);
    List<BarberService> findByBarbershopIdOrderByNameAsc(Long barbershopId);
    List<BarberService> findByBarbershopIdAndActiveTrueOrderByNameAsc(Long barbershopId);
}
