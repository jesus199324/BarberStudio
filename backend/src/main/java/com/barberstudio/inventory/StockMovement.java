package com.barberstudio.inventory;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Historial inmutable de cada cambio de stock. */
@Entity
@Table(name = "stock_movement")
@Getter @Setter @NoArgsConstructor
public class StockMovement extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    @Column(name = "product_id") private Long productId;
    private int delta;
    @Enumerated(EnumType.STRING) private MovementReason reason;
    @Column(name = "sale_id") private Long saleId;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "user_id") private Long userId;
    private String note;
}
