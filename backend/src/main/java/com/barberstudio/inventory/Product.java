package com.barberstudio.inventory;

import com.barberstudio.common.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Producto de cuidado. El stock solo cambia a traves de StockService (setter de paquete). */
@Entity
@Table(name = "product")
@Getter @Setter @NoArgsConstructor
public class Product extends BaseEntity {
    @Column(name = "barbershop_id") private Long barbershopId;
    private String name;
    private String description;
    private BigDecimal price;
    @Setter(AccessLevel.PACKAGE) private int stock;
    @Column(name = "low_stock_threshold") private int lowStockThreshold = 2;
    @Column(name = "image_url") private String imageUrl;
    @Column(name = "image_key") private String imageKey;
    private boolean active = true;
    @Column(name = "visible_in_shop") private boolean visibleInShop = true;

    public boolean isLowStock() { return stock <= lowStockThreshold; }
}
