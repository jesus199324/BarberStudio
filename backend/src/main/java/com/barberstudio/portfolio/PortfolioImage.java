package com.barberstudio.portfolio;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "portfolio_image")
@Getter @Setter @NoArgsConstructor
public class PortfolioImage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private PortfolioItem item;

    @Column(name = "storage_key") private String storageKey;
    private String url;
    @Column(name = "alt_text") private String altText;
    private int position;
    @Column(name = "is_cover") private boolean cover;
    @Column(name = "content_type") private String contentType;
    @Column(name = "size_bytes") private Long sizeBytes;
}
