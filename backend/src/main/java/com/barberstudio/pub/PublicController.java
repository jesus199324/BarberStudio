package com.barberstudio.pub;

import com.barberstudio.availability.AvailabilityService;
import com.barberstudio.availability.BookingService;
import com.barberstudio.barber.Barber;
import com.barberstudio.barber.BarberRepository;
import com.barberstudio.barber.WorkingHoursRepository;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.catalog.BarberServiceRepository;
import com.barberstudio.catalog.CatalogService.StyleResponse;
import com.barberstudio.catalog.HaircutStyleRepository;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.util.RateLimiter;
import com.barberstudio.common.util.WhatsAppLinks;
import com.barberstudio.common.web.PageResponse;
import com.barberstudio.portfolio.PortfolioService;
import com.barberstudio.portfolio.PortfolioService.ItemResponse;
import com.barberstudio.request.RequestService;
import com.barberstudio.request.RequestService.PublicRequest;
import com.barberstudio.request.RequestService.PublicRequestResponse;
import com.barberstudio.shop.Barbershop;
import com.barberstudio.shop.BarbershopRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** API publica: sin autenticacion, solo lectura de contenido publicado y alta de solicitudes. */
@RestController
@RequestMapping("/api/public/{slug}")
@RequiredArgsConstructor
public class PublicController {
    private final BarbershopRepository shops;
    private final BarberServiceRepository services;
    private final HaircutStyleRepository styles;
    private final PortfolioService portfolio;
    private final RequestService requestService;
    private final RateLimiter limiter;
    private final BarberRepository barberRepo;
    private final WorkingHoursRepository workingHours;
    private final AvailabilityService availability;
    private final BookingService booking;

    public record PublicService(Long id, String name, String description, BigDecimal price, int durationMinutes) {}

    public record PublicShop(String name, String slug, String aboutText, String address, String currency,
                             String whatsappUrl, List<PublicService> services, String logoUrl,
                             String instagramUrl, String tiktokUrl, String youtubeUrl, String neighborhood, String city,
                             String mapsUrl, String hoursNote, List<PublicHours> openingHours) {}

    public record PublicHours(int weekday, String open, String close) {}

    @GetMapping("/shop")
    @Transactional(readOnly = true)
    public PublicShop shop(@PathVariable String slug) {
        Barbershop b = shopBySlug(slug);
        var list = services.findByBarbershopIdAndActiveTrueOrderByNameAsc(b.getId()).stream()
            .map(s -> new PublicService(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getDurationMinutes())).toList();
        var hours = openingHours(b);
        return new PublicShop(b.getName(), b.getSlug(), b.getAboutText(), b.getAddress(), b.getCurrency(),
            WhatsAppLinks.link(b.getWhatsappE164(), "Hola, quisiera consultar por un turno."), list, b.getLogoUrl(),
            b.getInstagramUrl(), b.getTiktokUrl(), b.getYoutubeUrl(), b.getNeighborhood(), b.getCity(), b.getMapsUrl(), b.getHoursNote(), hours);
    }

    @GetMapping("/styles")
    @Transactional(readOnly = true)
    public List<StyleResponse> styles(@PathVariable String slug) {
        return styles.findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopBySlug(slug).getId())
            .stream().map(StyleResponse::from).toList();
    }

    @GetMapping("/portfolio")
    public PageResponse<ItemResponse> portfolio(@PathVariable String slug,
                                                @RequestParam(required = false) String style,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        return portfolio.searchPublic(shopBySlug(slug), style, q, page, size);
    }

    @GetMapping("/portfolio/{id}")
    public ItemResponse portfolioItem(@PathVariable String slug, @PathVariable Long id) {
        return portfolio.getPublic(shopBySlug(slug), id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicRequestResponse request(@PathVariable String slug, @Valid @RequestBody PublicRequest r, HttpServletRequest http) {
        if (!limiter.allow("request:" + http.getRemoteAddr(), 5, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Enviaste muchas solicitudes. Probá de nuevo más tarde.");
        }
        return requestService.createPublic(shopBySlug(slug), r);
    }

    public record PublicBarber(Long id, String name, String bio, String photoUrl, String specialties, List<Long> serviceIds) {}

    @GetMapping("/barbers")
    @Transactional(readOnly = true)
    public List<PublicBarber> barbers(@PathVariable String slug) {
        return barberRepo.findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(shopBySlug(slug).getId()).stream()
            .map(b -> new PublicBarber(b.getId(), b.getName(), b.getBio(), b.getPhotoUrl(), b.getSpecialties(),
                b.getServiceIds().stream().sorted().toList())).toList();
    }

    /** Estado de cada dia para el calendario: AVAILABLE, FULL, CLOSED (con motivo), PAST, FAR. */
    @GetMapping("/availability/days")
    public List<AvailabilityService.DayInfo> days(@PathVariable String slug, @RequestParam Long serviceId,
            @RequestParam(required = false) Long barberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Barbershop shop = shopBySlug(slug);
        return availability.days(shop, activeService(shop, serviceId), barberId, from, to);
    }

    @GetMapping("/availability/slots")
    public List<AvailabilityService.Slot> slots(@PathVariable String slug, @RequestParam Long serviceId,
            @RequestParam(required = false) Long barberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Barbershop shop = shopBySlug(slug);
        return availability.slots(shop, activeService(shop, serviceId), barberId, date);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingService.BookingResponse book(@PathVariable String slug, @Valid @RequestBody BookingService.BookingRequest r,
                                               HttpServletRequest http) {
        if (!limiter.allow("booking:" + http.getRemoteAddr(), 6, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Hiciste muchas reservas seguidas. Probá de nuevo más tarde.");
        }
        return booking.book(shopBySlug(slug), r);
    }

    /** Horario de atencion del negocio: por dia, desde la apertura mas temprana hasta el cierre mas tardio de los barberos activos. */
    private List<PublicHours> openingHours(Barbershop b) {
        List<Long> ids = barberRepo.findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(b.getId()).stream().map(Barber::getId).toList();
        Map<Integer, LocalTime[]> byDay = new TreeMap<>();
        if (!ids.isEmpty()) {
            workingHours.findByBarberIdIn(ids).forEach(w -> byDay.merge((int) w.getWeekday(),
                new LocalTime[]{w.getStartTime(), w.getEndTime()},
                (a, c) -> new LocalTime[]{a[0].isBefore(c[0]) ? a[0] : c[0], a[1].isAfter(c[1]) ? a[1] : c[1]}));
        }
        return byDay.entrySet().stream().map(e -> new PublicHours(e.getKey(), e.getValue()[0].toString(), e.getValue()[1].toString())).toList();
    }

    private BarberService activeService(Barbershop shop, Long id) {
        return services.findByIdAndBarbershopId(id, shop.getId()).filter(BarberService::isActive)
            .orElseThrow(() -> new NotFoundException("El servicio no está disponible."));
    }

    private Barbershop shopBySlug(String slug) {
        return shops.findBySlug(slug).orElseThrow(() -> new NotFoundException("Barbería no encontrada."));
    }
}
