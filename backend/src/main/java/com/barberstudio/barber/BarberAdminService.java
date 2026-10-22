package com.barberstudio.barber;

import com.barberstudio.appointment.AppointmentRepository;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.catalog.BarberServiceRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BarberAdminService {
    private final BarberRepository barbers;
    private final BarberServiceRepository services;
    private final WorkingHoursRepository hours;
    private final ScheduleOverrideRepository overrides;
    private final AppointmentRepository appointments;

    public record BarberRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String name,
                                @Size(max = 2000) String bio, @Size(max = 500) String photoUrl,
                                @Size(max = 255) String specialties, Boolean active, Integer sortOrder, Set<Long> serviceIds) {}

    public record BarberResponse(Long id, String name, String bio, String photoUrl, String specialties, boolean active,
                                 int sortOrder, List<Long> serviceIds, long futureAppointments) {}

    public record HoursItem(@NotNull @Min(1) @Max(7) Integer weekday, @NotNull(message = "Indicá la hora de inicio") LocalTime startTime,
                            @NotNull(message = "Indicá la hora de fin") LocalTime endTime, LocalTime breakStart, LocalTime breakEnd) {}

    public record HoursRequest(@NotNull @Valid List<HoursItem> items) {}

    public record OverrideRequest(@NotNull(message = "Indicá la fecha") LocalDate date, boolean dayOff, LocalTime startTime,
                                  LocalTime endTime, LocalTime breakStart, LocalTime breakEnd, @Size(max = 255) String note) {}

    public record OverrideResponse(Long id, LocalDate date, boolean dayOff, LocalTime startTime, LocalTime endTime,
                                   LocalTime breakStart, LocalTime breakEnd, String note) {}

    @Transactional(readOnly = true)
    public List<BarberResponse> list() {
        return barbers.findByBarbershopIdOrderBySortOrderAscNameAsc(CurrentUser.shopId()).stream().map(this::resp).toList();
    }

    @Transactional
    public BarberResponse create(BarberRequest r) {
        long shop = CurrentUser.shopId();
        Barber b = new Barber();
        b.setBarbershopId(shop);
        apply(b, r);
        if (r.serviceIds() == null) {   // por defecto realiza todos los servicios activos
            services.findByBarbershopIdAndActiveTrueOrderByNameAsc(shop).forEach(s -> b.getServiceIds().add(s.getId()));
        }
        return resp(barbers.saveAndFlush(b));
    }

    /** Al desactivar, la respuesta trae futureAppointments para avisar si quedan turnos por atender. */
    @Transactional
    public BarberResponse update(Long id, BarberRequest r) {
        Barber b = find(id);
        apply(b, r);
        return resp(barbers.saveAndFlush(b));
    }

    @Transactional
    public void delete(Long id) {
        Barber b = find(id);
        if (appointments.countByBarberId(id) > 0) {
            throw new BusinessRuleException("Este barbero tiene historial de turnos: desactivalo en lugar de borrarlo.");
        }
        barbers.delete(b);
    }

    @Transactional(readOnly = true)
    public List<HoursItem> hours(Long id) {
        find(id);
        return hours.findByBarberIdOrderByWeekday(id).stream()
            .map(w -> new HoursItem((int) w.getWeekday(), w.getStartTime(), w.getEndTime(), w.getBreakStart(), w.getBreakEnd())).toList();
    }

    /** Reemplaza el horario semanal completo del barbero. */
    @Transactional
    public List<HoursItem> setHours(Long id, List<HoursItem> items) {
        find(id);
        Set<Integer> seen = new HashSet<>();
        for (HoursItem i : items) {
            validateWindow(i.startTime(), i.endTime(), i.breakStart(), i.breakEnd());
            if (!seen.add(i.weekday())) throw new BusinessRuleException("Hay un día repetido en el horario semanal.");
        }
        hours.deleteAllFor(id);
        for (HoursItem i : items) {
            WorkingHours w = new WorkingHours();
            w.setBarberId(id);
            w.setWeekday(i.weekday().shortValue());
            w.setStartTime(i.startTime());
            w.setEndTime(i.endTime());
            w.setBreakStart(i.breakStart());
            w.setBreakEnd(i.breakEnd());
            hours.save(w);
        }
        hours.flush();
        return hours(id);
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> overrides(Long id) {
        find(id);
        return overrides.findByBarberIdOrderByWorkDate(id).stream().map(this::toResponse).toList();
    }

    /** Excepcion para una unica fecha (dia libre u horario distinto). Reemplaza la anterior de esa fecha. */
    @Transactional
    public OverrideResponse addOverride(Long id, OverrideRequest r) {
        find(id);
        if (!r.dayOff()) {
            if (r.startTime() == null || r.endTime() == null) throw new BusinessRuleException("Indicá el horario de ese día o marcalo como día libre.");
            validateWindow(r.startTime(), r.endTime(), r.breakStart(), r.breakEnd());
        }
        ScheduleOverride o = overrides.findByBarberIdAndWorkDate(id, r.date()).orElseGet(ScheduleOverride::new);
        o.setBarberId(id);
        o.setWorkDate(r.date());
        o.setDayOff(r.dayOff());
        o.setStartTime(r.dayOff() ? null : r.startTime());
        o.setEndTime(r.dayOff() ? null : r.endTime());
        o.setBreakStart(r.dayOff() ? null : r.breakStart());
        o.setBreakEnd(r.dayOff() ? null : r.breakEnd());
        o.setNote(r.note());
        return toResponse(overrides.saveAndFlush(o));
    }

    @Transactional
    public void deleteOverride(Long id, Long overrideId) {
        find(id);
        overrides.delete(overrides.findByIdAndBarberId(overrideId, id).orElseThrow(() -> new NotFoundException("Excepción no encontrada.")));
    }

    // ---- helpers ----
    private Barber find(Long id) {
        return barbers.findByIdAndBarbershopId(id, CurrentUser.shopId()).orElseThrow(() -> new NotFoundException("Barbero no encontrado."));
    }

    private void apply(Barber b, BarberRequest r) {
        b.setName(r.name().trim());
        b.setBio(r.bio());
        b.setPhotoUrl(r.photoUrl());
        b.setSpecialties(r.specialties());
        if (r.active() != null) b.setActive(r.active());
        if (r.sortOrder() != null) b.setSortOrder(r.sortOrder());
        if (r.serviceIds() != null) {
            Set<Long> valid = services.findByBarbershopIdOrderByNameAsc(CurrentUser.shopId()).stream()
                .map(BarberService::getId).collect(Collectors.toSet());
            if (!valid.containsAll(r.serviceIds())) throw new BusinessRuleException("Hay servicios que no existen.");
            b.getServiceIds().clear();
            b.getServiceIds().addAll(r.serviceIds());
        }
    }

    private void validateWindow(LocalTime s, LocalTime e, LocalTime bs, LocalTime be) {
        if (!e.isAfter(s)) throw new BusinessRuleException("La hora de fin debe ser posterior a la de inicio.");
        if ((bs == null) != (be == null)) throw new BusinessRuleException("El descanso necesita hora de inicio y de fin.");
        if (bs != null && (!be.isAfter(bs) || bs.isBefore(s) || be.isAfter(e))) {
            throw new BusinessRuleException("El descanso debe estar dentro de la jornada.");
        }
    }

    private BarberResponse resp(Barber b) {
        long future = appointments.countByBarberIdAndStartAtAfterAndStatusIn(b.getId(), Instant.now(),
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
        return new BarberResponse(b.getId(), b.getName(), b.getBio(), b.getPhotoUrl(), b.getSpecialties(), b.isActive(),
            b.getSortOrder(), b.getServiceIds().stream().sorted().toList(), future);
    }

    private OverrideResponse toResponse(ScheduleOverride o) {
        return new OverrideResponse(o.getId(), o.getWorkDate(), o.isDayOff(), o.getStartTime(), o.getEndTime(),
            o.getBreakStart(), o.getBreakEnd(), o.getNote());
    }
}
