package com.barberstudio.catalog;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "haircut_style")
@Getter @Setter @NoArgsConstructor
public class HaircutStyle extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    private String slug;
    private String category = "CORTE";
    @Column(name = "sort_order") private int sortOrder;
    private boolean active = true;
}
