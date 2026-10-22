package com.barberstudio.catalog;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Servicio del catalogo (corte, barba, combo). Tabla "service". */
@Entity
@Table(name = "service")
@Getter @Setter @NoArgsConstructor
public class BarberService extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    private String description;
    private BigDecimal price;
    @Column(name = "duration_minutes") private int durationMinutes;
    private boolean active = true;
}
