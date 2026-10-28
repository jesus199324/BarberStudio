package com.barberstudio.shop;

import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.util.Phone;
import com.barberstudio.portfolio.ImageStorage;
import com.barberstudio.portfolio.ImageValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final BarbershopRepository shops;
    private final ImageStorage storage;

    public SettingsController(BarbershopRepository shops, ImageStorage storage) {
        this.shops = shops;
        this.storage = storage;
    }

    public record SettingsRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String name,
        @Size(max = 3000) String aboutText,
        @NotBlank(message = "El WhatsApp es obligatorio") String whatsapp,
        @Size(max = 255) String address,
        @NotBlank String timezone,
        @NotNull @Min(5) @Max(120) Integer slotMinutes,
        @NotNull @Min(0) @Max(120) Integer bufferMinutes,
        boolean autoConfirm,
        @NotNull @Min(1) @Max(365) Integer maxDaysAhead,
        @NotNull @Min(0) @Max(10080) Integer minNoticeMinutes) {}

    public record SettingsResponse(String name, String slug, String aboutText, String whatsapp, String address,
                                   String timezone, String currency, int slotMinutes, int bufferMinutes,
                                   boolean autoConfirm, int maxDaysAhead, int minNoticeMinutes, String logoUrl) {
        static SettingsResponse from(Barbershop b) {
            return new SettingsResponse(b.getName(), b.getSlug(), b.getAboutText(), b.getWhatsappE164(), b.getAddress(),
                b.getTimezone(), b.getCurrency(), b.getSlotMinutes(), b.getBufferMinutes(), b.isAutoConfirm(),
                b.getMaxDaysAhead(), b.getMinNoticeMinutes(), b.getLogoUrl());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public SettingsResponse get() { return SettingsResponse.from(find()); }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public SettingsResponse update(@Valid @RequestBody SettingsRequest r) {
        Barbershop b = find();
        try { ZoneId.of(r.timezone()); } catch (DateTimeException e) { throw new BusinessRuleException("Zona horaria inválida."); }
        b.setName(r.name().trim());
        b.setAboutText(r.aboutText());
        b.setWhatsappE164(Phone.normalize(r.whatsapp()));
        b.setAddress(r.address());
        b.setTimezone(r.timezone());
        b.setSlotMinutes(r.slotMinutes());
        b.setBufferMinutes(r.bufferMinutes());
        b.setAutoConfirm(r.autoConfirm());
        b.setMaxDaysAhead(r.maxDaysAhead());
        b.setMinNoticeMinutes(r.minNoticeMinutes());
        return SettingsResponse.from(shops.save(b));
    }

    /** Imagen de portada opcional (logo o foto del barbero). Mismas validaciones que las fotos del portfolio. */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public SettingsResponse uploadLogo(@RequestPart("file") MultipartFile file) {
        Barbershop b = find();
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new UncheckedIOException(e); }
        String ext = ImageValidator.extensionFor(bytes);
        var stored = storage.store(b.getId(), bytes, ext);
        String old = b.getLogoKey();
        b.setLogoUrl(stored.url());
        b.setLogoKey(stored.key());
        shops.saveAndFlush(b);
        if (old != null) storage.delete(old);
        return SettingsResponse.from(b);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public SettingsResponse deleteLogo() {
        Barbershop b = find();
        String old = b.getLogoKey();
        b.setLogoUrl(null);
        b.setLogoKey(null);
        shops.saveAndFlush(b);
        if (old != null) storage.delete(old);
        return SettingsResponse.from(b);
    }

    private Barbershop find() {
        return shops.findById(CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Barbería no encontrada."));
    }
}
