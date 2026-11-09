package com.barberstudio.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findTop100ByProductIdOrderByCreatedAtDescIdDesc(Long productId);
}
