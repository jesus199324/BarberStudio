package com.barberstudio.portfolio;

import com.barberstudio.catalog.HaircutStyle;
import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "portfolio_item")
@Getter @Setter @NoArgsConstructor
public class PortfolioItem extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "style_id")
    private HaircutStyle style;

    private String title;
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Enumerated(EnumType.STRING) private PortfolioStatus status = PortfolioStatus.DRAFT;
    @Column(name = "published_at") private Instant publishedAt;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    @BatchSize(size = 50)
    private List<PortfolioImage> images = new ArrayList<>();
}
