package com.barberstudio.catalog;

import com.barberstudio.barber.BarberRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.util.Slugs;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicios (catalogo de precios) y estilos de corte. No se borran: se desactivan, porque hay historial que los referencia. */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final BarberServiceRepository services;
    private final HaircutStyleRepository styles;
    private final BarberRepository barbers;

    public record ServiceRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0", message = "El precio no puede ser negativo") BigDecimal price,
        @NotNull(message = "La duración es obligatoria") @Min(value = 5, message = "Mínimo 5 minutos") @Max(value = 480, message = "Máximo 480 minutos") Integer durationMinutes,
        Boolean active) {}

    public record ServiceResponse(Long id, String name, String description, BigDecimal price, int durationMinutes, boolean active) {
        public static ServiceResponse from(BarberService s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getDurationMinutes(), s.isActive());
        }
    }

    public record StyleRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String name,
                               @Pattern(regexp = "CORTE|COLOR|BARBA|TATUAJE", message = "Categoría inválida") String category,
                               Integer sortOrder, Boolean active) {}

    public record StyleResponse(Long id, String name, String slug, String category, int sortOrder, boolean active) {
        public static StyleResponse from(HaircutStyle s) {
            return new StyleResponse(s.getId(), s.getName(), s.getSlug(), s.getCategory(), s.getSortOrder(), s.isActive());
        }
    }

    // ---- services ----
    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(boolean activeOnly) {
        long shop = CurrentUser.shopId();
        var list = activeOnly ? services.findByBarbershopIdAndActiveTrueOrderByNameAsc(shop)
                              : services.findByBarbershopIdOrderByNameAsc(shop);
        return list.stream().map(ServiceResponse::from).toList();
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest r) {
        BarberService s = new BarberService();
        s.setBarbershopId(CurrentUser.shopId());
        apply(s, r);
        BarberService saved = services.saveAndFlush(s);
        // Un servicio nuevo lo realizan, por defecto, los barberos existentes (se puede ajustar por barbero).
        barbers.findByBarbershopIdOrderBySortOrderAscNameAsc(saved.getBarbershopId()).forEach(b -> b.getServiceIds().add(saved.getId()));
        return ServiceResponse.from(saved);
    }

    @Transactional
    public ServiceResponse updateService(Long id, ServiceRequest r) {
        BarberService s = services.findByIdAndBarbershopId(id, CurrentUser.shopId())
            .orElseThrow(() -> new NotFoundException("Servicio no encontrado."));
        apply(s, r);
        return ServiceResponse.from(services.saveAndFlush(s));
    }

    private void apply(BarberService s, ServiceRequest r) {
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setPrice(r.price());
        s.setDurationMinutes(r.durationMinutes());
        if (r.active() != null) s.setActive(r.active());
    }

    // ---- styles ----
    @Transactional(readOnly = true)
    public List<StyleResponse> listStyles(boolean activeOnly) {
        long shop = CurrentUser.shopId();
        var list = activeOnly ? styles.findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(shop)
                              : styles.findByBarbershopIdOrderBySortOrderAscNameAsc(shop);
        return list.stream().map(StyleResponse::from).toList();
    }

    @Transactional
    public StyleResponse createStyle(StyleRequest r) {
        HaircutStyle s = new HaircutStyle();
        s.setBarbershopId(CurrentUser.shopId());
        applyStyle(s, r);
        return StyleResponse.from(styles.saveAndFlush(s));
    }

    @Transactional
    public StyleResponse updateStyle(Long id, StyleRequest r) {
        HaircutStyle s = styles.findByIdAndBarbershopId(id, CurrentUser.shopId())
            .orElseThrow(() -> new NotFoundException("Estilo no encontrado."));
        applyStyle(s, r);
        return StyleResponse.from(styles.saveAndFlush(s));
    }

    private void applyStyle(HaircutStyle s, StyleRequest r) {
        String slug = Slugs.slugify(r.name());
        if (slug.isEmpty()) throw new BusinessRuleException("El nombre del estilo no es válido.");
        s.setName(r.name().trim());
        s.setSlug(slug);
        if (r.category() != null) s.setCategory(r.category());
        if (r.sortOrder() != null) s.setSortOrder(r.sortOrder());
        if (r.active() != null) s.setActive(r.active());
    }
}
