package com.barberstudio.closure;

import com.barberstudio.appointment.Appointment;
import com.barberstudio.appointment.AppointmentRepository;
import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.barber.Barber;
import com.barberstudio.barber.BarberRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.ConflictException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.shop.BarbershopRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierres generales y licencias por barbero. Si un bloqueo afecta turnos ya reservados, NUNCA se cancelan solos:
 * hay que autorizar el bloqueo (authorizeAffected) y, aparte y de forma explicita, pedir su cancelacion (cancelAffected).
 */
@Service
@RequiredArgsConstructor
public class ClosureService {
    private final ClosureRepository closures;
    private final BarberRepository barbers;
    private final AppointmentRepository appointments;
    private final BarbershopRepository shops;

    public record ClosureRequest(
        Long barberId,
        @NotNull(message = "Indicá la fecha de inicio") LocalDate startDate,
        @NotNull(message = "Indicá la fecha de fin") LocalDate endDate,
        LocalTime startTime, LocalTime endTime,
        @NotNull(message = "Elegí un motivo") @Pattern(regexp = "FERIADO|VACACIONES|CIERRE_EXTRAORDINARIO|MANTENIMIENTO|PERSONAL|LICENCIA|OTRO", message = "Motivo inválido") String reason,
        @Size(max = 2000) String notes,
        boolean authorizeAffected, boolean cancelAffected) {}

    public record ClosureResponse(Long id, Long barberId, String barberName, LocalDate startDate, LocalDate endDate,
                                  LocalTime startTime, LocalTime endTime, String reason, String notes) {}

    public record ClosureResult(ClosureResponse closure, List<AppointmentResponse> affected, boolean cancelled) {}

    @Transactional(readOnly = true)
    public List<ClosureResponse> list(LocalDate from, LocalDate to) {
        long shop = CurrentUser.shopId();
        Map<Long, String> names = barberNames(shop);
        return closures.findByBarbershopIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDate(shop, from, to)
            .stream().map(c -> toResponse(c, names)).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> preview(ClosureRequest r) {
        validate(r);
        return affected(CurrentUser.shopId(), r).stream().map(AppointmentResponse::from).toList();
    }

    @Transactional
    public ClosureResult create(ClosureRequest r) {
        Closure c = new Closure();
        c.setBarbershopId(CurrentUser.shopId());
        return save(c, r);
    }

    @Transactional
    public ClosureResult update(Long id, ClosureRequest r) {
        return save(find(id), r);
    }

    @Transactional
    public void delete(Long id) { closures.delete(find(id)); }

    private ClosureResult save(Closure c, ClosureRequest r) {
        long shop = CurrentUser.shopId();
        validate(r);
        if (r.cancelAffected() && !r.authorizeAffected()) throw new BusinessRuleException("Para cancelar turnos afectados tenés que autorizarlo.");
        List<Appointment> hit = affected(shop, r);
        if (!hit.isEmpty() && !r.authorizeAffected()) {
            throw new ConflictException("Este bloqueo afecta " + hit.size() + " turno(s) ya reservados. Revisalos y autorizá para continuar.",
                Map.of("affected", hit.stream().map(AppointmentResponse::from).toList()));
        }
        c.setBarberId(r.barberId());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setStartTime(r.startTime());
        c.setEndTime(r.endTime());
        c.setReason(r.reason());
        c.setNotes(r.notes());
        Closure saved = closures.saveAndFlush(c);
        boolean cancelled = false;
        if (r.cancelAffected()) {   // consentimiento explicito del dueño
            hit.forEach(a -> { a.setStatus(AppointmentStatus.CANCELLED); a.setUpdatedAt(Instant.now()); });
            cancelled = !hit.isEmpty();
        }
        return new ClosureResult(toResponse(saved, barberNames(shop)), hit.stream().map(AppointmentResponse::from).toList(), cancelled);
    }

    private void validate(ClosureRequest r) {
        if (r.endDate().isBefore(r.startDate())) throw new BusinessRuleException("La fecha de fin no puede ser anterior a la de inicio.");
        if (ChronoUnit.DAYS.between(r.startDate(), r.endDate()) > 400) throw new BusinessRuleException("El período es demasiado largo.");
        if ((r.startTime() == null) != (r.endTime() == null)) throw new BusinessRuleException("Indicá hora de inicio y de fin, o dejá ambas vacías para bloquear el día completo.");
        if (r.startTime() != null && !r.endTime().isAfter(r.startTime())) throw new BusinessRuleException("La hora de fin debe ser posterior a la de inicio.");
        if (r.barberId() != null) {
            barbers.findByIdAndBarbershopId(r.barberId(), CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Barbero no encontrado."));
        }
    }

    /** Turnos abiertos que caen dentro del bloqueo (de un barbero o de todos). */
    private List<Appointment> affected(long shop, ClosureRequest r) {
        ZoneId zone = ZoneId.of(shops.findById(shop).orElseThrow().getTimezone());
        Instant from = r.startDate().atStartOfDay(zone).toInstant();
        Instant to = r.endDate().plusDays(1).atStartOfDay(zone).toInstant();
        return appointments.findOpenInRange(shop, from, to).stream()
            .filter(a -> r.barberId() == null || a.getBarber().getId().equals(r.barberId()))
            .filter(a -> {
                for (LocalDate d = r.startDate(); !d.isAfter(r.endDate()); d = d.plusDays(1)) {
                    Instant cs = r.startTime() == null ? d.atStartOfDay(zone).toInstant() : ZonedDateTime.of(d, r.startTime(), zone).toInstant();
                    Instant ce = r.endTime() == null ? d.plusDays(1).atStartOfDay(zone).toInstant() : ZonedDateTime.of(d, r.endTime(), zone).toInstant();
                    if (a.getStartAt().isBefore(ce) && a.getEndAt().isAfter(cs)) return true;
                }
                return false;
            }).toList();
    }

    private Closure find(Long id) {
        return closures.findByIdAndBarbershopId(id, CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Bloqueo no encontrado."));
    }

    private Map<Long, String> barberNames(long shop) {
        return barbers.findByBarbershopIdOrderBySortOrderAscNameAsc(shop).stream().collect(Collectors.toMap(Barber::getId, Barber::getName, (a, b) -> a));
    }

    private ClosureResponse toResponse(Closure c, Map<Long, String> names) {
        return new ClosureResponse(c.getId(), c.getBarberId(), c.getBarberId() == null ? null : names.get(c.getBarberId()),
            c.getStartDate(), c.getEndDate(), c.getStartTime(), c.getEndTime(), c.getReason(), c.getNotes());
    }
}
