package com.barberstudio.portfolio;

import com.barberstudio.catalog.HaircutStyle;
import com.barberstudio.catalog.HaircutStyleRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.util.WhatsAppLinks;
import com.barberstudio.common.web.PageResponse;
import com.barberstudio.shop.Barbershop;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private static final int MAX_IMAGES = 8;

    private final PortfolioItemRepository items;
    private final HaircutStyleRepository styles;
    private final ImageStorage storage;

    public record ItemRequest(@NotNull(message = "Elegí un estilo") Long styleId,
                              @NotBlank(message = "El título es obligatorio") @Size(max = 140) String title,
                              @Size(max = 2000) String description,
                              List<String> tags) {}

    public record ImageResponse(Long id, String url, String altText, int position, boolean cover) {}

    public record StyleRef(Long id, String name, String slug) {}

    public record ItemResponse(Long id, String title, String description, StyleRef style, List<String> tags,
                               PortfolioStatus status, Instant publishedAt, String coverUrl,
                               List<ImageResponse> images, String whatsappUrl) {}

    // ================= publico =================
    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> searchPublic(Barbershop shop, String styleSlug, String q, int page, int size) {
        long styleId = 0L;
        if (styleSlug != null && !styleSlug.isBlank()) {
            styleId = styles.findByBarbershopIdAndSlug(shop.getId(), styleSlug).map(HaircutStyle::getId).orElse(-1L);
        }
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
            Sort.by(Sort.Direction.DESC, "publishedAt"));
        return PageResponse.of(items.searchPublished(shop.getId(), styleId, pattern, pageable).map(i -> toResponse(i, shop)));
    }

    @Transactional(readOnly = true)
    public ItemResponse getPublic(Barbershop shop, Long id) {
        return items.findByIdAndBarbershopIdAndStatus(id, shop.getId(), PortfolioStatus.PUBLISHED)
            .map(i -> toResponse(i, shop)).orElseThrow(() -> new NotFoundException("Publicación no encontrada."));
    }

    // ================= privado =================
    @Transactional(readOnly = true)
    public List<ItemResponse> list(PortfolioStatus status) {
        long shop = CurrentUser.shopId();
        var list = status == null ? items.findByBarbershopIdOrderByCreatedAtDesc(shop)
                                  : items.findByBarbershopIdAndStatusOrderByCreatedAtDesc(shop, status);
        return list.stream().map(i -> toResponse(i, null)).toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id) { return toResponse(find(id), null); }

    @Transactional
    public ItemResponse create(ItemRequest r) {
        PortfolioItem item = new PortfolioItem();
        item.setBarbershopId(CurrentUser.shopId());
        apply(item, r);
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest r) {
        PortfolioItem item = find(id);
        apply(item, r);
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse publish(Long id) {
        PortfolioItem item = find(id);
        if (item.getImages().isEmpty()) throw new BusinessRuleException("Agregá al menos una imagen antes de publicar.");
        if (!item.getStyle().isActive()) throw new BusinessRuleException("El estilo de la publicación está inactivo.");
        item.setStatus(PortfolioStatus.PUBLISHED);
        if (item.getPublishedAt() == null) item.setPublishedAt(Instant.now());
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse unpublish(Long id) {
        PortfolioItem item = find(id);
        item.setStatus(PortfolioStatus.DRAFT);
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse archive(Long id) {
        PortfolioItem item = find(id);
        item.setStatus(PortfolioStatus.ARCHIVED);
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse addImage(Long id, MultipartFile file, String alt) {
        PortfolioItem item = find(id);
        if (item.getImages().size() >= MAX_IMAGES) throw new BusinessRuleException("Máximo " + MAX_IMAGES + " imágenes por publicación.");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new UncheckedIOException(e); }
        String ext = ImageValidator.extensionFor(bytes);
        var stored = storage.store(item.getBarbershopId(), bytes, ext);

        PortfolioImage img = new PortfolioImage();
        img.setItem(item);
        img.setStorageKey(stored.key());
        img.setUrl(stored.url());
        img.setAltText(alt == null || alt.isBlank() ? item.getTitle() : alt.trim());
        img.setPosition(item.getImages().size());
        img.setCover(item.getImages().isEmpty());
        img.setContentType(ImageValidator.mimeFor(ext));
        img.setSizeBytes((long) bytes.length);
        item.getImages().add(img);
        try {
            return toResponse(items.saveAndFlush(item), null);
        } catch (RuntimeException e) {
            storage.delete(stored.key());   // evita archivos huerfanos si falla la base de datos
            throw e;
        }
    }

    @Transactional
    public ItemResponse setCover(Long id, Long imageId) {
        PortfolioItem item = find(id);
        PortfolioImage target = item.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
            .orElseThrow(() -> new NotFoundException("Imagen no encontrada."));
        item.getImages().forEach(i -> i.setCover(false));
        items.saveAndFlush(item);            // el indice unico admite una sola portada: primero se limpia
        target.setCover(true);
        return toResponse(items.saveAndFlush(item), null);
    }

    @Transactional
    public ItemResponse deleteImage(Long id, Long imageId) {
        PortfolioItem item = find(id);
        PortfolioImage img = item.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
            .orElseThrow(() -> new NotFoundException("Imagen no encontrada."));
        if (item.getStatus() == PortfolioStatus.PUBLISHED && item.getImages().size() == 1) {
            throw new BusinessRuleException("No podés borrar la única imagen de una publicación publicada.");
        }
        item.getImages().remove(img);
        items.saveAndFlush(item);
        if (img.isCover() && !item.getImages().isEmpty()) {
            item.getImages().get(0).setCover(true);
            items.saveAndFlush(item);
        }
        storage.delete(img.getStorageKey());
        return toResponse(item, null);
    }

    @Transactional
    public void delete(Long id) {
        PortfolioItem item = find(id);
        List<String> keys = item.getImages().stream().map(PortfolioImage::getStorageKey).toList();
        items.delete(item);
        items.flush();
        keys.forEach(storage::delete);
    }

    // ---- helpers ----
    private PortfolioItem find(Long id) {
        return items.findByIdAndBarbershopId(id, CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Publicación no encontrada."));
    }

    private void apply(PortfolioItem item, ItemRequest r) {
        item.setStyle(styles.findByIdAndBarbershopId(r.styleId(), item.getBarbershopId())
            .orElseThrow(() -> new NotFoundException("Estilo no encontrado.")));
        item.setTitle(r.title().trim());
        item.setDescription(r.description());
        item.setTags(normalizeTags(r.tags()));
    }

    private String[] normalizeTags(List<String> tags) {
        if (tags == null) return new String[0];
        return tags.stream().filter(t -> t != null && !t.isBlank()).map(t -> t.trim().toLowerCase(Locale.ROOT))
            .map(t -> t.length() > 30 ? t.substring(0, 30) : t).distinct().limit(10).toArray(String[]::new);
    }

    private ItemResponse toResponse(PortfolioItem i, Barbershop shopForLink) {
        List<ImageResponse> imgs = i.getImages().stream()
            .map(m -> new ImageResponse(m.getId(), m.getUrl(), m.getAltText(), m.getPosition(), m.isCover())).toList();
        String cover = imgs.stream().filter(ImageResponse::cover).map(ImageResponse::url).findFirst()
            .orElse(imgs.isEmpty() ? null : imgs.get(0).url());
        String wa = shopForLink == null ? null : WhatsAppLinks.link(shopForLink.getWhatsappE164(),
            "Hola, vi el corte " + i.getTitle() + " en tu portfolio y me gustaría consultar disponibilidad para hacerme uno.");
        return new ItemResponse(i.getId(), i.getTitle(), i.getDescription(),
            new StyleRef(i.getStyle().getId(), i.getStyle().getName(), i.getStyle().getSlug()),
            Arrays.asList(i.getTags()), i.getStatus(), i.getPublishedAt(), cover, imgs, wa);
    }
}
