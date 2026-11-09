package com.barberstudio.inventory;

import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.ConflictException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.portfolio.ImageStorage;
import com.barberstudio.portfolio.ImageValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {
    /** Motivos que puede cargar una persona; el resto los genera el sistema (ventas, pedidos, anulaciones). */
    private static final Set<MovementReason> MANUAL_REASONS = Set.of(MovementReason.PURCHASE, MovementReason.ADJUSTMENT);

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final StockService stock;
    private final ImageStorage images;

    public record ProductRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotNull(message = "El precio es obligatorio") @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Min(0) Integer lowStockThreshold,
        Boolean active,
        Boolean visibleInShop) {}

    public record ProductResponse(Long id, String name, String description, BigDecimal price, int stock,
                                  int lowStockThreshold, boolean lowStock, String imageUrl, boolean active,
                                  boolean visibleInShop) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock(),
                p.getLowStockThreshold(), p.isLowStock(), p.getImageUrl(), p.isActive(), p.isVisibleInShop());
        }
    }

    public record StockAdjustRequest(@NotNull Integer delta, @NotNull MovementReason reason, @Size(max = 255) String note) {}

    public record MovementResponse(Long id, int delta, MovementReason reason, String note, Long userId, Instant createdAt) {
        public static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getId(), m.getDelta(), m.getReason(), m.getNote(), m.getUserId(), m.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(boolean lowStockOnly) {
        long shop = CurrentUser.shopId();
        List<Product> result = lowStockOnly ? products.findLowStock(shop) : products.findByBarbershopIdOrderByNameAsc(shop);
        return result.stream().map(ProductResponse::from).toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        long shop = CurrentUser.shopId();
        String name = r.name().trim();
        if (products.existsByBarbershopIdAndNameIgnoreCase(shop, name)) throw duplicate();
        Product p = new Product();
        p.setBarbershopId(shop);
        apply(p, r, name);
        return ProductResponse.from(products.saveAndFlush(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        long shop = CurrentUser.shopId();
        Product p = find(id);
        String name = r.name().trim();
        if (products.existsByBarbershopIdAndNameIgnoreCaseAndIdNot(shop, name, id)) throw duplicate();
        apply(p, r, name);
        return ProductResponse.from(products.saveAndFlush(p));
    }

    @Transactional
    public ProductResponse adjustStock(Long id, StockAdjustRequest r) {
        if (!MANUAL_REASONS.contains(r.reason())) throw new BusinessRuleException("Motivo no permitido.");
        if (r.delta() == 0) throw new BusinessRuleException("La cantidad no puede ser 0.");
        if (r.reason() == MovementReason.PURCHASE && r.delta() < 0) {
            throw new BusinessRuleException("La reposición tiene que sumar unidades.");
        }
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();
        if (r.reason() == MovementReason.ADJUSTMENT && note == null) {
            throw new BusinessRuleException("Indicá el motivo del ajuste.");
        }
        stock.move(new StockChange(CurrentUser.shopId(), id, r.delta(), r.reason(), CurrentUser.userId(), null, null, note));
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> movements(Long id) {
        Product p = find(id);
        return movements.findTop100ByProductIdOrderByCreatedAtDescIdDesc(p.getId()).stream()
            .map(MovementResponse::from).toList();
    }

    /** Guarda la foto nueva y, recien despues de grabar en la base, borra la anterior. */
    @Transactional
    public ProductResponse setImage(Long id, MultipartFile file) {
        Product p = find(id);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new UncheckedIOException(e); }
        String ext = ImageValidator.extensionFor(bytes);
        var stored = images.store(p.getBarbershopId(), bytes, ext);
        String oldKey = p.getImageKey();
        p.setImageKey(stored.key());
        p.setImageUrl(stored.url());
        try {
            products.saveAndFlush(p);
        } catch (RuntimeException e) {
            images.delete(stored.key());   // evita archivos huerfanos si falla la base de datos
            throw e;
        }
        if (oldKey != null) images.delete(oldKey);
        return ProductResponse.from(p);
    }

    @Transactional
    public void removeImage(Long id) {
        Product p = find(id);
        String oldKey = p.getImageKey();
        p.setImageKey(null);
        p.setImageUrl(null);
        products.saveAndFlush(p);
        if (oldKey != null) images.delete(oldKey);
    }

    Product find(Long id) {
        return products.findByIdAndBarbershopId(id, CurrentUser.shopId())
            .orElseThrow(() -> new NotFoundException("Producto no encontrado."));
    }

    /** En PUT, los opcionales en null conservan el valor actual; en un producto nuevo quedan los valores por defecto. */
    private static void apply(Product p, ProductRequest r, String name) {
        p.setName(name);
        p.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        p.setPrice(r.price());
        if (r.lowStockThreshold() != null) p.setLowStockThreshold(r.lowStockThreshold());
        if (r.active() != null) p.setActive(r.active());
        if (r.visibleInShop() != null) p.setVisibleInShop(r.visibleInShop());
    }

    private static ConflictException duplicate() {
        return new ConflictException("Ya existe un producto con ese nombre.");
    }
}
