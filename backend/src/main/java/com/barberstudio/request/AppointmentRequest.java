package com.barberstudio.request;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Solicitud enviada desde la web publica. NO es un turno ni bloquea horarios hasta que el barbero la acepta. */
@Entity
@Table(name = "appointment_request")
@Getter @Setter @NoArgsConstructor
public class AppointmentRequest extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    @Column(name = "whatsapp_e164") private String whatsappE164;
    @Column(name = "style_id") private Long styleId;
    @Column(name = "portfolio_item_id") private Long portfolioItemId;
    private String message;
    @Column(name = "preferred_time_text") private String preferredTimeText;
    @Enumerated(EnumType.STRING) private RequestStatus status = RequestStatus.NEW;
    @Column(name = "appointment_id") private Long appointmentId;
}
