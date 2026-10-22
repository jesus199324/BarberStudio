package com.barberstudio.closure;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cierre general (barberId nulo) o licencia de un barbero. Sin horas = dia completo. */
@Entity
@Table(name = "closure")
@Getter @Setter @NoArgsConstructor
public class Closure extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    @Column(name = "barber_id") private Long barberId;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "start_time") private LocalTime startTime;
    @Column(name = "end_time") private LocalTime endTime;
    private String reason;
    private String notes;
}
