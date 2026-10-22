package com.barberstudio.availability;

import com.barberstudio.appointment.AppointmentRepository;
import com.barberstudio.appointment.Busy;
import com.barberstudio.barber.*;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.closure.Closure;
import com.barberstudio.closure.ClosureRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.shop.Barbershop;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula la disponibilidad real. Para cada barbero y dia:
 *   ventana de trabajo (excepcion de fecha o horario semanal, menos el descanso)
 *   - cierres generales y licencias del barbero
 *   - turnos existentes (ampliados por el tiempo de preparacion)
 * y propone inicios alineados a la grilla (slot_minutes) donde ENTRA la duracion real del servicio.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {
    private static final Map<String, String> REASONS = Map.of(
        "FERIADO", "Feriado", "VACACIONES", "Cerrado por vacaciones", "CIERRE_EXTRAORDINARIO", "Cierre extraordinario",
        "MANTENIMIENTO", "Cerrado por mantenimiento", "PERSONAL", "Cerrado", "LICENCIA", "Cerrado", "OTRO", "Cerrado");

    private final BarberRepository barbers;
    private final WorkingHoursRepository hours;
    private final ScheduleOverrideRepository overrides;
    private final ClosureRepository closures;
    private final AppointmentRepository appointments;

    public record Slot(Instant startAt, String label, List<Long> barberIds) {}
    public record DayInfo(LocalDate date, String status, String reason) {}
    private record Iv(long s, long e) {}
    private record Day(String status, String reason, List<Slot> slots) {}
    private record Ctx(Barbershop shop, ZoneId zone, List<Barber> barbers,
                       Map<Long, Map<Integer, WorkingHours>> hours, Map<Long, Map<LocalDate, ScheduleOverride>> overrides,
                       List<Closure> closures, List<Busy> busy) {}

    /** barberId nulo = "cualquier barbero". */
    @Transactional(readOnly = true)
    public List<Slot> slots(Barbershop shop, BarberService svc, Long barberId, LocalDate date) {
        return day(load(shop, svc, barberId, date, date), svc, date).slots();
    }

    /** Estado de cada dia (para el calendario): AVAILABLE, FULL, CLOSED, PAST, FAR. */
    @Transactional(readOnly = true)
    public List<DayInfo> days(Barbershop shop, BarberService svc, Long barberId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 62) throw new BusinessRuleException("Rango de fechas inválido.");
        Ctx c = load(shop, svc, barberId, from, to);
        List<DayInfo> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Day r = day(c, svc, d);
            out.add(new DayInfo(d, r.status(), r.reason()));
        }
        return out;
    }

    private Ctx load(Barbershop shop, BarberService svc, Long barberId, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(shop.getTimezone());
        List<Barber> bs = barbers.findByBarbershopIdAndActiveTrueOrderBySortOrderAscNameAsc(shop.getId()).stream()
            .filter(b -> b.getServiceIds().contains(svc.getId()) && (barberId == null || b.getId().equals(barberId))).toList();
        List<Long> ids = bs.stream().map(Barber::getId).toList();
        Map<Long, Map<Integer, WorkingHours>> h = new HashMap<>();
        Map<Long, Map<LocalDate, ScheduleOverride>> o = new HashMap<>();
        if (!ids.isEmpty()) {
            hours.findByBarberIdIn(ids).forEach(w -> h.computeIfAbsent(w.getBarberId(), k -> new HashMap<>()).put((int) w.getWeekday(), w));
            overrides.findByBarberIdInAndWorkDateBetween(ids, from, to)
                .forEach(x -> o.computeIfAbsent(x.getBarberId(), k -> new HashMap<>()).put(x.getWorkDate(), x));
        }
        List<Closure> cl = closures.findByBarbershopIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDate(shop.getId(), from, to);
        Instant a = from.atStartOfDay(zone).toInstant().minus(Duration.ofDays(1));
        Instant b = to.plusDays(1).atStartOfDay(zone).toInstant().plus(Duration.ofDays(1));
        return new Ctx(shop, zone, bs, h, o, cl, appointments.busyBetween(shop.getId(), a, b));
    }

    private Day day(Ctx c, BarberService svc, LocalDate d) {
        LocalDate today = LocalDate.now(c.zone());
        if (d.isBefore(today)) return new Day("PAST", null, List.of());
        if (d.isAfter(today.plusDays(c.shop().getMaxDaysAhead()))) return new Day("FAR", null, List.of());
        long dayStart = d.atStartOfDay(c.zone()).toInstant().toEpochMilli();
        long dayEnd = d.plusDays(1).atStartOfDay(c.zone()).toInstant().toEpochMilli();
        for (Closure cl : c.closures()) {
            if (cl.getBarberId() == null && cl.getStartTime() == null && covers(cl, d)) {
                return new Day("CLOSED", REASONS.getOrDefault(cl.getReason(), "Cerrado"), List.of());
            }
        }
        long step = c.shop().getSlotMinutes() * 60_000L, dur = svc.getDurationMinutes() * 60_000L, buf = c.shop().getBufferMinutes() * 60_000L;
        long minStart = System.currentTimeMillis() + c.shop().getMinNoticeMinutes() * 60_000L;
        boolean workable = false;
        TreeMap<Long, List<Long>> starts = new TreeMap<>();
        Map<Long, Integer> load = new HashMap<>();
        for (Barber b : c.barbers()) {
            List<Iv> free = windows(c, b, d);
            for (Closure cl : c.closures()) {
                if ((cl.getBarberId() != null && !cl.getBarberId().equals(b.getId())) || !covers(cl, d)) continue;
                long cs = cl.getStartTime() == null ? dayStart : ms(c, d, cl.getStartTime());
                long ce = cl.getEndTime() == null ? dayEnd : ms(c, d, cl.getEndTime());
                free = subtract(free, cs, ce);
            }
            if (free.isEmpty()) continue;
            workable = true;
            for (Busy x : c.busy()) {
                if (!x.barberId().equals(b.getId())) continue;
                long s = x.startAt().toEpochMilli(), e = x.endAt().toEpochMilli();
                if (e > dayStart && s < dayEnd) load.merge(b.getId(), 1, Integer::sum);
                free = subtract(free, s - buf, e + buf);
            }
            for (Iv f : free) {
                long s = dayStart + ((f.s() - dayStart + step - 1) / step) * step;   // alinea a la grilla
                for (; s + dur <= f.e(); s += step) {
                    if (s >= minStart) starts.computeIfAbsent(s, k -> new ArrayList<>()).add(b.getId());
                }
            }
        }
        if (!workable) return new Day("CLOSED", null, List.of());
        List<Slot> slots = starts.entrySet().stream().map(en -> {
            List<Long> ids = new ArrayList<>(en.getValue());
            // Con "cualquier barbero" se prefiere al que tiene menos turnos ese dia.
            ids.sort(Comparator.comparingInt((Long id) -> load.getOrDefault(id, 0)).thenComparing(Comparator.naturalOrder()));
            Instant t = Instant.ofEpochMilli(en.getKey());
            return new Slot(t, t.atZone(c.zone()).toLocalTime().toString(), ids);
        }).toList();
        return new Day(slots.isEmpty() ? "FULL" : "AVAILABLE", null, slots);
    }

    private List<Iv> windows(Ctx c, Barber b, LocalDate d) {
        LocalTime s, e, bs, be;
        ScheduleOverride o = c.overrides().getOrDefault(b.getId(), Map.of()).get(d);
        if (o != null) {
            if (o.isDayOff()) return List.of();
            s = o.getStartTime(); e = o.getEndTime(); bs = o.getBreakStart(); be = o.getBreakEnd();
        } else {
            WorkingHours w = c.hours().getOrDefault(b.getId(), Map.of()).get(d.getDayOfWeek().getValue());
            if (w == null) return List.of();
            s = w.getStartTime(); e = w.getEndTime(); bs = w.getBreakStart(); be = w.getBreakEnd();
        }
        List<Iv> out = new ArrayList<>();
        if (bs == null || be == null) out.add(new Iv(ms(c, d, s), ms(c, d, e)));
        else { out.add(new Iv(ms(c, d, s), ms(c, d, bs))); out.add(new Iv(ms(c, d, be), ms(c, d, e))); }
        return out;
    }

    private static boolean covers(Closure cl, LocalDate d) { return !d.isBefore(cl.getStartDate()) && !d.isAfter(cl.getEndDate()); }

    private static long ms(Ctx c, LocalDate d, LocalTime t) { return ZonedDateTime.of(d, t, c.zone()).toInstant().toEpochMilli(); }

    private static List<Iv> subtract(List<Iv> in, long cs, long ce) {
        List<Iv> out = new ArrayList<>();
        for (Iv f : in) {
            if (ce <= f.s() || cs >= f.e()) { out.add(f); continue; }
            if (cs > f.s()) out.add(new Iv(f.s(), cs));
            if (ce < f.e()) out.add(new Iv(ce, f.e()));
        }
        return out;
    }
}
