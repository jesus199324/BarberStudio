package com.barberstudio.barber;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "barber")
@Getter @Setter @NoArgsConstructor
public class Barber extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    private String bio;
    @Column(name = "photo_url") private String photoUrl;
    private String specialties;
    private boolean active = true;
    @Column(name = "sort_order") private int sortOrder;

    /** Servicios que realiza este barbero. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "barber_service", joinColumns = @JoinColumn(name = "barber_id"))
    @Column(name = "service_id")
    private Set<Long> serviceIds = new HashSet<>();
}
