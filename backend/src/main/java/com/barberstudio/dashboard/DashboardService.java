package com.barberstudio.dashboard;

import com.barberstudio.appointment.Appointment;
import com.barberstudio.appointment.AppointmentRepository;
import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.client.ClientRepository;
import com.barberstudio.client.ClientService.ClientResponse;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.request.AppointmentRequestRepository;
import com.barberstudio.request.RequestStatus;
import com.barberstudio.shop.Barbershop;
import com.barberstudio.shop.BarbershopRepository;
import com.barberstudio.visit.VisitRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final BarbershopRepository shops;
    private final AppointmentRepository appointments;
    private final VisitRepository visits;
    private final ClientRepository clients;
    private final AppointmentRequestRepository requests;

    /** Los ingresos son "registrados" (visitas completadas y cobradas). No se calcula ganancia neta: no hay costos cargados. */
    public record Summary(LocalDate date, String timezone, String currency,
                          List<AppointmentResponse> todayAppointments, List<AppointmentResponse> upcoming,
                          long pendingToConfirm, long cancelledToday, long completedToday, BigDecimal registeredIncomeToday,
                          long completedWeek, BigDecimal registeredIncomeWeek,
                          long completedMonth, BigDecimal registeredIncomeMonth,
                          long newRequests, List<ClientResponse> recentClients) {}

    @Transactional(readOnly = true)
    public Summary summary() {
        long shopId = CurrentUser.shopId();
        Barbershop shop = shops.findById(shopId).orElseThrow();
        ZoneId zone = ZoneId.of(shop.getTimezone());
        Instant now = Instant.now();

        LocalDate today = LocalDate.now(zone);
        Instant dayFrom = today.atStartOfDay(zone).toInstant();
        Instant dayTo = today.plusDays(1).atStartOfDay(zone).toInstant();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekFrom = weekStart.atStartOfDay(zone).toInstant();
        Instant weekTo = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant monthFrom = monthStart.atStartOfDay(zone).toInstant();
        Instant monthTo = monthStart.plusMonths(1).atStartOfDay(zone).toInstant();

        List<Appointment> todays = appointments.findInRange(shopId, dayFrom, dayTo);
        List<AppointmentResponse> upcoming = todays.stream()
            .filter(a -> (a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED)
                         && a.getEndAt().isAfter(now))
            .limit(5).map(AppointmentResponse::from).toList();

        return new Summary(today, shop.getTimezone(), shop.getCurrency(),
            todays.stream().map(AppointmentResponse::from).toList(), upcoming,
            appointments.countByBarbershopIdAndStatusAndEndAtAfter(shopId, AppointmentStatus.PENDING, now),
            todays.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count(),
            visits.countBetween(shopId, dayFrom, dayTo), visits.incomeBetween(shopId, dayFrom, dayTo),
            visits.countBetween(shopId, weekFrom, weekTo), visits.incomeBetween(shopId, weekFrom, weekTo),
            visits.countBetween(shopId, monthFrom, monthTo), visits.incomeBetween(shopId, monthFrom, monthTo),
            requests.countByBarbershopIdAndStatus(shopId, RequestStatus.NEW),
            clients.findTop5ByBarbershopIdAndArchivedAtIsNullOrderByCreatedAtDesc(shopId).stream().map(ClientResponse::from).toList());
    }
}
