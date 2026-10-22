package com.barberstudio.client;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "client")
@Getter @Setter @NoArgsConstructor
public class Client extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    @Column(name = "whatsapp_e164") private String whatsappE164;
    private String preferences;
    private String notes;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Column(name = "marketing_opt_in") private boolean marketingOptIn;
    @Column(name = "marketing_opt_in_at") private Instant marketingOptInAt;
}
