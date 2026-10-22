package com.barberstudio.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HaircutStyleRepository extends JpaRepository<HaircutStyle, Long> {
    Optional<HaircutStyle> findByIdAndBarbershopId(Long id, Long barbershopId);
    Optional<HaircutStyle> findByBarbershopIdAndSlug(Long barbershopId, String slug);
    List<HaircutStyle> findByBarbershopIdOrderBySortOrderAscNameAsc(Long barbershopId);
    List<HaircutStyle> findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(Long barbershopId);
}
