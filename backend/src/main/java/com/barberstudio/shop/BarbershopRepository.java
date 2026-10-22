package com.barberstudio.shop;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarbershopRepository extends JpaRepository<Barbershop, Long> {
    Optional<Barbershop> findBySlug(String slug);
}
