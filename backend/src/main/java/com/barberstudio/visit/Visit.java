package com.barberstudio.visit;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Servicio efectivamente realizado y cobrado. Guarda una instantanea (nombre y precio) del momento. */
@Entity
@Table(name = "visit")
@Getter @Setter @NoArgsConstructor
public class Visit extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    @Column(name = "appointment_id") private Long appointmentId;
    @Column(name = "client_id") private Long clientId;
    @Column(name = "performed_at") private Instant performedAt;
    @Column(name = "service_name") private String serviceName;
    @Column(name = "final_price") private BigDecimal finalPrice;
    @Column(name = "style_id") private Long styleId;
    @Column(name = "portfolio_item_id") private Long portfolioItemId;
    private String notes;
}
