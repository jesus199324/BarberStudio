package com.barberstudio.auth;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Enumerated(EnumType.STRING) private Role role;
    private boolean active = true;
}
