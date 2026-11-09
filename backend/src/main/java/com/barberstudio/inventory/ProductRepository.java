package com.barberstudio.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndBarbershopId(Long id, Long barbershopId);

    List<Product> findByBarbershopIdOrderByNameAsc(Long barbershopId);

    @Query("select p from Product p where p.barbershopId = :shop and p.stock <= p.lowStockThreshold order by p.name")
    List<Product> findLowStock(@Param("shop") Long shop);

    boolean existsByBarbershopIdAndNameIgnoreCase(Long barbershopId, String name);

    boolean existsByBarbershopIdAndNameIgnoreCaseAndIdNot(Long barbershopId, String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.barbershopId = :shop")
    Optional<Product> lockById(@Param("id") Long id, @Param("shop") Long shop);
}
