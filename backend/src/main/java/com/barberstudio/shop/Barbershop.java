package com.barberstudio.shop;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "barbershop")
@Getter @Setter @NoArgsConstructor
public class Barbershop extends BaseEntity {
    private String name;
    private String slug;
    @Column(name = "about_text") private String aboutText;
    @Column(name = "whatsapp_e164") private String whatsappE164;
    private String address;
    private String timezone;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(length = 3) private String currency;
    @Column(name = "slot_minutes") private int slotMinutes = 15;
    @Column(name = "buffer_minutes") private int bufferMinutes;
    @Column(name = "auto_confirm") private boolean autoConfirm;
    @Column(name = "max_days_ahead") private int maxDaysAhead = 60;
    @Column(name = "min_notice_minutes") private int minNoticeMinutes = 60;
}
