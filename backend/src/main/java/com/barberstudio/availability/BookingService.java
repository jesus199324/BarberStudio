package com.barberstudio.availability;

import com.barberstudio.appointment.Appointment;
import com.barberstudio.appointment.AppointmentRepository;
import com.barberstudio.appointment.AppointmentService;
import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.availability.AvailabilityService.Slot;
import com.barberstudio.catalog.BarberService;
import com.barberstudio.catalog.BarberServiceRepository;
import com.barberstudio.client.Client;
import com.barberstudio.client.ClientRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.ConflictException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.util.Phone;
import com.barberstudio.common.util.WhatsAppLinks;
import com.barberstudio.portfolio.PortfolioItemRepository;
import com.barberstudio.portfolio.PortfolioStatus;
import com.barberstudio.shop.Barbershop;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserva publica. El horario se VUELVE A VALIDAR en el servidor contra el motor de disponibilidad y,
 * ademas, la restriccion de exclusion de PostgreSQL impide que dos clientes tomen el mismo intervalo.
 */
@Service
@RequiredArgsConstructor
public class BookingService {
    private final AvailabilityService availability;
    private final BarberServiceRepository services;
    private final ClientRepository clients;
    private final AppointmentService appointmentService;
    private final AppointmentRepository appointments;
    private final PortfolioItemRepository portfolio;
    private final PlatformTransactionManager tm;

    public record BookingRequest(
        @NotNull(message = "Elegí un servicio") Long serviceId,
        Long barberId,
        @NotNull(message = "Elegí fecha y hora") Instant startAt,
        @NotBlank(message = "Ingresá tu nombre") @Size(max = 120) String name,
        @NotBlank(message = "Ingresá tu WhatsApp") String whatsapp,
        LocalDate birthDate,
        boolean marketingOptIn,
        @Size(max = 500, message = "Máximo 500 caracteres") String notes,
        Long portfolioItemId,
        String website) {}   // campo trampa (honeypot)

    public record BookingResponse(String serviceName, String barberName, Instant startAt, Instant endAt,
                                  BigDecimal price, AppointmentStatus status, String message, String whatsappUrl) {}

    public BookingResponse book(Barbershop shop, BookingRequest r) {
        ZoneId zone = ZoneId.of(shop.getTimezone());
        if (r.website() != null && !r.website().isBlank()) {   // bot: respuesta normal, no se guarda nada
            return new BookingResponse(null, null, r.startAt(), r.startAt(), null, AppointmentStatus.PENDING,
                "Recibimos tu reserva.", WhatsAppLinks.link(shop.getWhatsappE164(), "Hola, quisiera consultar por un turno."));
        }
        BarberService svc = services.findByIdAndBarbershopId(r.serviceId(), shop.getId()).filter(BarberService::isActive)
            .orElseThrow(() -> new NotFoundException("El servicio no está disponible."));
        String phone = Phone.normalize(r.whatsapp());
        if (r.birthDate() != null && r.birthDate().isAfter(LocalDate.now(zone))) {
            throw new BusinessRuleException("La fecha de nacimiento no es válida.");
        }
        LocalDate date = r.startAt().atZone(zone).toLocalDate();
        Slot slot = availability.slots(shop, svc, r.barberId(), date).stream()
            .filter(s -> s.startAt().equals(r.startAt())).findFirst()
            .orElseThrow(() -> new ConflictException("Ese horario ya no está disponible. Elegí otro."));

        Client client = inNewTx(() -> upsertClient(shop.getId(), r, phone));
        long upcoming = appointments.countByClientIdAndStartAtAfterAndStatusIn(client.getId(), Instant.now(),
            List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
        if (upcoming >= 3) {
            throw new BusinessRuleException("Ya tenés varios turnos próximos. Si necesitás otro, escribinos por WhatsApp.");
        }
        AppointmentStatus status = shop.isAutoConfirm() ? AppointmentStatus.CONFIRMED : AppointmentStatus.PENDING;
        String notes = buildNotes(shop, r);

        for (Long barberId : slot.barberIds()) {   // con "cualquier barbero" se prueba en orden de menor carga
            try {
                return inNewTx(() -> {
                    Appointment a = appointmentService.createForShop(shop.getId(), client.getId(), svc.getId(), barberId,
                        r.startAt(), null, null, status, notes);
                    return response(shop, zone, AppointmentResponse.from(a));
                });
            } catch (ConflictException | DataIntegrityViolationException e) {
                // otro cliente tomo ese intervalo justo ahora: se intenta con el siguiente barbero
            }
        }
        throw new ConflictException("Ese horario se acaba de ocupar. Elegí otro.");
    }

    private Client upsertClient(Long shopId, BookingRequest r, String phone) {
        Client c = clients.findByBarbershopIdAndWhatsappE164AndArchivedAtIsNull(shopId, phone).orElseGet(() -> {
            Client n = new Client();
            n.setBarbershopId(shopId);
            n.setName(r.name().trim());
            n.setWhatsappE164(phone);
            return n;
        });
        if (c.getBirthDate() == null && r.birthDate() != null) c.setBirthDate(r.birthDate());
        if (r.marketingOptIn() && !c.isMarketingOptIn()) {
            c.setMarketingOptIn(true);
            c.setMarketingOptInAt(Instant.now());
        }
        return clients.saveAndFlush(c);
    }

    private String buildNotes(Barbershop shop, BookingRequest r) {
        StringBuilder sb = new StringBuilder("Reserva desde la web.");
        if (r.portfolioItemId() != null) {
            portfolio.findByIdAndBarbershopId(r.portfolioItemId(), shop.getId())
                .filter(i -> i.getStatus() == PortfolioStatus.PUBLISHED)
                .ifPresent(i -> sb.append(" Referencia del portfolio: ").append(i.getTitle()).append('.'));
        }
        if (r.notes() != null && !r.notes().isBlank()) sb.append(" Comentario del cliente: ").append(r.notes().trim());
        return sb.toString();
    }

    private BookingResponse response(Barbershop shop, ZoneId zone, AppointmentResponse a) {
        String when = a.startAt().atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", new Locale("es", "UY")));
        String msg = a.status() == AppointmentStatus.CONFIRMED
            ? "Tu turno está confirmado."
            : "Recibimos tu reserva. Queda pendiente hasta que el barbero la confirme y te avise por WhatsApp.";
        String wa = WhatsAppLinks.link(shop.getWhatsappE164(),
            "Hola, soy " + a.clientName() + ". Reservé " + a.serviceName() + " con " + a.barberName() + " para el " + when + ".");
        return new BookingResponse(a.serviceName(), a.barberName(), a.startAt(), a.endAt(), a.priceAgreed(), a.status(), msg, wa);
    }

    private <T> T inNewTx(Supplier<T> action) {
        TransactionTemplate t = new TransactionTemplate(tm);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t.execute(status -> action.get());
    }
}
