package com.barberstudio.appointment;

import com.barberstudio.barber.Barber;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.client.Client;
import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointment")
@Getter @Setter @NoArgsConstructor
public class Appointment extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private BarberService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id")
    private Barber barber;

    @Column(name = "start_at") private Instant startAt;
    @Column(name = "end_at") private Instant endAt;
    /** Instantanea del catalogo al reservar: cambiar el catalogo no altera turnos existentes. */
    @Column(name = "price_agreed") private BigDecimal priceAgreed;
    @Column(name = "duration_minutes") private int durationMinutes;
    @Enumerated(EnumType.STRING) private AppointmentStatus status;
    private String notes;
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();
}
