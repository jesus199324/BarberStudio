package com.barberstudio.appointment;

import com.barberstudio.barber.Barber;
import com.barberstudio.barber.BarberRepository;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.catalog.BarberServiceRepository;
import com.barberstudio.catalog.HaircutStyleRepository;
import com.barberstudio.client.Client;
import com.barberstudio.client.ClientRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.ConflictException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.portfolio.PortfolioItemRepository;
import com.barberstudio.shop.BarbershopRepository;
import com.barberstudio.visit.Visit;
import com.barberstudio.visit.VisitRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la agenda (cada turno pertenece a un barbero; no se solapan POR BARBERO):
 *  PENDING -> CONFIRMED | CANCELLED ; CONFIRMED -> COMPLETED | CANCELLED ; COMPLETED y CANCELLED son finales.
 * Completar crea la Visit en la misma transaccion; un turno cancelado nunca genera visita ni ingreso.
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final ClientRepository clients;
    private final BarberServiceRepository services;
    private final BarberRepository barbers;
    private final HaircutStyleRepository styles;
    private final PortfolioItemRepository portfolio;
    private final VisitRepository visits;
    private final BarbershopRepository shops;

    public record CreateRequest(
        @NotNull(message = "Elegí un cliente") Long clientId,
        @NotNull(message = "Elegí un servicio") Long serviceId,
        @NotNull(message = "Elegí un barbero") Long barberId,
        @NotNull(message = "Indicá fecha y hora") Instant startAt,
        @Min(5) @Max(480) Integer durationMinutes,
        @DecimalMin("0") BigDecimal priceAgreed,
        AppointmentStatus status,
        @Size(max = 2000) String notes) {}

    public record UpdateRequest(
        @NotNull(message = "Indicá fecha y hora") Instant startAt,
        @Min(5) @Max(480) Integer durationMinutes,
        @DecimalMin("0") BigDecimal priceAgreed,
        @Size(max = 2000) String notes) {}

    public record ReassignRequest(@NotNull(message = "Elegí un barbero") Long barberId) {}

    public record CompleteRequest(@DecimalMin("0") BigDecimal finalPrice, Long styleId, Long portfolioItemId,
                                  @Size(max = 2000) String notes) {}

    public record AppointmentResponse(Long id, Long clientId, String clientName, String clientWhatsapp,
                                      Long serviceId, String serviceName, Instant startAt, Instant endAt,
                                      int durationMinutes, BigDecimal priceAgreed, AppointmentStatus status, String notes,
                                      Long barberId, String barberName) {
        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(a.getId(), a.getClient().getId(), a.getClient().getName(),
                a.getClient().getWhatsappE164(), a.getService().getId(), a.getService().getName(),
                a.getStartAt(), a.getEndAt(), a.getDurationMinutes(), a.getPriceAgreed(), a.getStatus(), a.getNotes(),
                a.getBarber().getId(), a.getBarber().getName());
        }
    }

    public record VisitResponse(Long id, Long appointmentId, Long clientId, Instant performedAt, String serviceName,
                                BigDecimal finalPrice, Long styleId, Long portfolioItemId, String notes) {
        public static VisitResponse from(Visit v) {
            return new VisitResponse(v.getId(), v.getAppointmentId(), v.getClientId(), v.getPerformedAt(),
                v.getServiceName(), v.getFinalPrice(), v.getStyleId(), v.getPortfolioItemId(), v.getNotes());
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(LocalDate from, LocalDate to, Long barberId) {
        long shop = CurrentUser.shopId();
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) {
            throw new BusinessRuleException("Rango de fechas inválido (máximo 62 días).");
        }
        ZoneId zone = ZoneId.of(shops.findById(shop).orElseThrow().getTimezone());
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        var list = barberId == null ? appointments.findInRange(shop, start, end)
                                    : appointments.findInRangeByBarber(shop, barberId, start, end);
        return list.stream().map(AppointmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) { return AppointmentResponse.from(findDetail(id)); }

    @Transactional(readOnly = true)
    public VisitResponse visitOf(Long appointmentId) {
        findDetail(appointmentId);
        return visits.findByAppointmentIdAndBarbershopId(appointmentId, CurrentUser.shopId())
            .map(VisitResponse::from).orElseThrow(() -> new NotFoundException("Este turno todavía no tiene visita registrada."));
    }

    @Transactional
    public AppointmentResponse create(CreateRequest r) {
        Appointment a = createForShop(CurrentUser.shopId(), r.clientId(), r.serviceId(), r.barberId(), r.startAt(),
            r.durationMinutes(), r.priceAgreed(), r.status(), r.notes());
        return AppointmentResponse.from(a);
    }

    /** Tambien lo usan la reserva publica y la aceptacion de solicitudes. */
    @Transactional
    public Appointment createForShop(long shop, Long clientId, Long serviceId, Long barberId, Instant start,
                                     Integer duration, BigDecimal price, AppointmentStatus status, String notes) {
        AppointmentStatus initial = status == null ? AppointmentStatus.CONFIRMED : status;
        if (initial != AppointmentStatus.PENDING && initial != AppointmentStatus.CONFIRMED) {
            throw new BusinessRuleException("Un turno nuevo solo puede crearse como pendiente o confirmado.");
        }
        Client client = clients.findByIdAndBarbershopId(clientId, shop)
            .orElseThrow(() -> new NotFoundException("Cliente no encontrado."));
        if (client.getArchivedAt() != null) throw new BusinessRuleException("El cliente está archivado.");
        BarberService service = services.findByIdAndBarbershopId(serviceId, shop)
            .orElseThrow(() -> new NotFoundException("Servicio no encontrado."));
        if (!service.isActive()) throw new BusinessRuleException("El servicio está inactivo.");
        Barber barber = requireBarberFor(shop, barberId, serviceId);

        int minutes = duration != null ? duration : service.getDurationMinutes();
        Instant end = start.plus(Duration.ofMinutes(minutes));
        ensureFree(barber.getId(), start, end, -1L);

        Appointment a = new Appointment();
        a.setBarbershopId(shop);
        a.setClient(client);
        a.setService(service);
        a.setBarber(barber);
        a.setStartAt(start);
        a.setEndAt(end);
        a.setDurationMinutes(minutes);                                   // instantanea
        a.setPriceAgreed(price != null ? price : service.getPrice());    // instantanea
        a.setStatus(initial);
        a.setNotes(notes);
        return appointments.saveAndFlush(a);
    }

    @Transactional
    public AppointmentResponse update(Long id, UpdateRequest r) {
        Appointment a = findDetail(id);
        requireOpen(a);
        int minutes = r.durationMinutes() != null ? r.durationMinutes() : a.getDurationMinutes();
        Instant end = r.startAt().plus(Duration.ofMinutes(minutes));
        ensureFree(a.getBarber().getId(), r.startAt(), end, a.getId());
        a.setStartAt(r.startAt());
        a.setEndAt(end);
        a.setDurationMinutes(minutes);
        if (r.priceAgreed() != null) a.setPriceAgreed(r.priceAgreed());
        if (r.notes() != null) a.setNotes(r.notes());
        a.setUpdatedAt(Instant.now());
        return AppointmentResponse.from(appointments.saveAndFlush(a));
    }

    /** Pasa un turno abierto a otro barbero (que realice el servicio y este libre). */
    @Transactional
    public AppointmentResponse reassign(Long id, Long barberId) {
        Appointment a = findDetail(id);
        requireOpen(a);
        Barber barber = requireBarberFor(a.getBarbershopId(), barberId, a.getService().getId());
        ensureFree(barber.getId(), a.getStartAt(), a.getEndAt(), a.getId());
        a.setBarber(barber);
        a.setUpdatedAt(Instant.now());
        return AppointmentResponse.from(appointments.saveAndFlush(a));
    }

    @Transactional
    public AppointmentResponse confirm(Long id) {
        Appointment a = findDetail(id);
        if (a.getStatus() != AppointmentStatus.PENDING) throw new BusinessRuleException("Solo se pueden confirmar turnos pendientes.");
        a.setStatus(AppointmentStatus.CONFIRMED);
        a.setUpdatedAt(Instant.now());
        return AppointmentResponse.from(a);
    }

    @Transactional
    public AppointmentResponse cancel(Long id) {
        Appointment a = findDetail(id);
        requireOpen(a);
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setUpdatedAt(Instant.now());
        return AppointmentResponse.from(a);
    }

    /** Idempotente: completar dos veces el mismo turno devuelve la misma visita, sin duplicar ingresos. */
    @Transactional
    public VisitResponse complete(Long id, CompleteRequest r) {
        long shop = CurrentUser.shopId();
        Appointment a = appointments.lockById(id, shop).orElseThrow(() -> new NotFoundException("Turno no encontrado."));
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            return visits.findByAppointmentId(a.getId()).map(VisitResponse::from)
                .orElseThrow(() -> new IllegalStateException("Turno completado sin visita: " + a.getId()));
        }
        if (a.getStatus() == AppointmentStatus.CANCELLED) throw new BusinessRuleException("Un turno cancelado no puede completarse.");
        if (a.getStatus() != AppointmentStatus.CONFIRMED) throw new BusinessRuleException("Confirmá el turno antes de completarlo.");

        CompleteRequest req = r != null ? r : new CompleteRequest(null, null, null, null);
        if (req.styleId() != null) {
            styles.findByIdAndBarbershopId(req.styleId(), shop).orElseThrow(() -> new NotFoundException("Estilo no encontrado."));
        }
        if (req.portfolioItemId() != null) {
            portfolio.findByIdAndBarbershopId(req.portfolioItemId(), shop)
                .orElseThrow(() -> new NotFoundException("Publicación no encontrada."));
        }
        Visit v = new Visit();
        v.setBarbershopId(shop);
        v.setAppointmentId(a.getId());
        v.setClientId(a.getClient().getId());
        v.setPerformedAt(a.getStartAt());                 // fecha del servicio = fecha del turno
        v.setServiceName(a.getService().getName());       // instantanea historica
        v.setFinalPrice(req.finalPrice() != null ? req.finalPrice() : a.getPriceAgreed());
        v.setStyleId(req.styleId());
        v.setPortfolioItemId(req.portfolioItemId());
        v.setNotes(req.notes());
        Visit saved = visits.saveAndFlush(v);
        a.setStatus(AppointmentStatus.COMPLETED);
        a.setUpdatedAt(Instant.now());
        return VisitResponse.from(saved);
    }

    // ---- helpers ----
    private Appointment findDetail(Long id) {
        return appointments.findDetail(id, CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Turno no encontrado."));
    }

    private Barber requireBarberFor(long shop, Long barberId, Long serviceId) {
        Barber b = barbers.findByIdAndBarbershopId(barberId, shop).orElseThrow(() -> new NotFoundException("Barbero no encontrado."));
        if (!b.isActive()) throw new BusinessRuleException("El barbero está inactivo.");
        if (!b.getServiceIds().contains(serviceId)) throw new BusinessRuleException("Ese barbero no realiza el servicio elegido.");
        return b;
    }

    private void requireOpen(Appointment a) {
        if (a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Un turno completado o cancelado no se puede modificar.");
        }
    }

    /** Mensaje claro para el caso normal; la restriccion de exclusion en PostgreSQL cubre las carreras. */
    private void ensureFree(Long barberId, Instant start, Instant end, Long excludeId) {
        if (appointments.existsOverlap(barberId, start, end, excludeId)) {
            throw new ConflictException("Ese horario se superpone con otro turno del barbero.", Map.of("startAt", start.toString()));
        }
    }
}
