package com.barberstudio.request;

import com.barberstudio.appointment.Appointment;
import com.barberstudio.appointment.AppointmentService;
import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.appointment.AppointmentStatus;
import com.barberstudio.catalog.HaircutStyleRepository;
import com.barberstudio.client.Client;
import com.barberstudio.client.ClientRepository;
import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.util.Phone;
import com.barberstudio.common.util.WhatsAppLinks;
import com.barberstudio.portfolio.PortfolioItemRepository;
import com.barberstudio.portfolio.PortfolioStatus;
import com.barberstudio.shop.Barbershop;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestService {
    private final AppointmentRequestRepository requests;
    private final HaircutStyleRepository styles;
    private final PortfolioItemRepository portfolio;
    private final ClientRepository clients;
    private final AppointmentService appointmentService;

    public record PublicRequest(
        @NotBlank(message = "Ingresá tu nombre") @Size(max = 120) String name,
        @NotBlank(message = "Ingresá tu WhatsApp") String whatsapp,
        Long styleId, Long portfolioItemId,
        @Size(max = 1000, message = "Máximo 1000 caracteres") String message,
        @Size(max = 160) String preferredTimeText,
        String website) {}   // "website" es un campo trampa (honeypot): las personas no lo ven ni lo completan

    public record PublicRequestResponse(String message, String whatsappUrl) {}

    public record RequestResponse(Long id, String name, String whatsapp, Long styleId, Long portfolioItemId,
                                  String message, String preferredTimeText, RequestStatus status,
                                  Long appointmentId, Instant createdAt, String whatsappUrl) {
        static RequestResponse from(AppointmentRequest r) {
            String text = "Hola " + r.getName() + ", recibí tu solicitud de corte. Coordinemos el horario.";
            return new RequestResponse(r.getId(), r.getName(), r.getWhatsappE164(), r.getStyleId(), r.getPortfolioItemId(),
                r.getMessage(), r.getPreferredTimeText(), r.getStatus(), r.getAppointmentId(), r.getCreatedAt(),
                WhatsAppLinks.link(r.getWhatsappE164(), text));
        }
    }

    public record AcceptRequest(Long clientId, @NotNull(message = "Elegí un barbero") Long barberId,
                                @NotNull(message = "Elegí un servicio") Long serviceId,
                                @NotNull(message = "Indicá fecha y hora") Instant startAt,
                                @Min(5) @Max(480) Integer durationMinutes,
                                @DecimalMin("0") BigDecimal priceAgreed,
                                @Size(max = 2000) String notes) {}

    // ---- publico ----
    @Transactional
    public PublicRequestResponse createPublic(Barbershop shop, PublicRequest r) {
        String confirmation = "Recibimos tu solicitud. Todavía no es una reserva: te confirmamos el horario por WhatsApp.";
        String shopLink = WhatsAppLinks.link(shop.getWhatsappE164(),
            "Hola, soy " + r.name().trim() + ". Acabo de enviar una solicitud de corte desde la web.");
        if (r.website() != null && !r.website().isBlank()) {
            return new PublicRequestResponse(confirmation, shopLink);   // bot: respuesta normal, sin guardar nada
        }
        String phone = Phone.normalize(r.whatsapp());
        if (requests.countByBarbershopIdAndWhatsappE164AndStatusAndCreatedAtAfter(
                shop.getId(), phone, RequestStatus.NEW, Instant.now().minus(Duration.ofHours(24))) >= 3) {
            throw new BusinessRuleException("Ya recibimos varias solicitudes con este número. Te vamos a responder pronto.");
        }
        if (r.styleId() != null) {
            styles.findByIdAndBarbershopId(r.styleId(), shop.getId()).orElseThrow(() -> new BusinessRuleException("Estilo inválido."));
        }
        if (r.portfolioItemId() != null) {
            portfolio.findByIdAndBarbershopId(r.portfolioItemId(), shop.getId())
                .filter(i -> i.getStatus() == PortfolioStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessRuleException("La publicación indicada no existe."));
        }
        AppointmentRequest req = new AppointmentRequest();
        req.setBarbershopId(shop.getId());
        req.setName(r.name().trim());
        req.setWhatsappE164(phone);
        req.setStyleId(r.styleId());
        req.setPortfolioItemId(r.portfolioItemId());
        req.setMessage(r.message());
        req.setPreferredTimeText(r.preferredTimeText());
        requests.save(req);
        return new PublicRequestResponse(confirmation, shopLink);
    }

    // ---- privado ----
    @Transactional(readOnly = true)
    public List<RequestResponse> list(RequestStatus status) {
        long shop = CurrentUser.shopId();
        var list = status == null ? requests.findByBarbershopIdOrderByCreatedAtDesc(shop)
                                  : requests.findByBarbershopIdAndStatusOrderByCreatedAtDesc(shop, status);
        return list.stream().map(RequestResponse::from).toList();
    }

    @Transactional
    public AppointmentResponse accept(Long id, AcceptRequest r) {
        long shop = CurrentUser.shopId();
        AppointmentRequest req = findNew(id, shop);
        Client client;
        if (r.clientId() != null) {
            client = clients.findByIdAndBarbershopId(r.clientId(), shop).orElseThrow(() -> new NotFoundException("Cliente no encontrado."));
        } else {
            client = clients.findByBarbershopIdAndWhatsappE164AndArchivedAtIsNull(shop, req.getWhatsappE164()).orElseGet(() -> {
                Client c = new Client();
                c.setBarbershopId(shop);
                c.setName(req.getName());
                c.setWhatsappE164(req.getWhatsappE164());
                return clients.saveAndFlush(c);
            });
        }
        Appointment a = appointmentService.createForShop(shop, client.getId(), r.serviceId(), r.barberId(), r.startAt(),
            r.durationMinutes(), r.priceAgreed(), AppointmentStatus.CONFIRMED, r.notes());
        req.setStatus(RequestStatus.ACCEPTED);
        req.setAppointmentId(a.getId());
        requests.saveAndFlush(req);
        return AppointmentResponse.from(a);
    }

    @Transactional
    public RequestResponse reject(Long id) {
        AppointmentRequest req = findNew(id, CurrentUser.shopId());
        req.setStatus(RequestStatus.REJECTED);
        return RequestResponse.from(requests.saveAndFlush(req));
    }

    private AppointmentRequest findNew(Long id, long shop) {
        AppointmentRequest req = requests.findByIdAndBarbershopId(id, shop).orElseThrow(() -> new NotFoundException("Solicitud no encontrada."));
        if (req.getStatus() != RequestStatus.NEW) throw new BusinessRuleException("La solicitud ya fue procesada.");
        return req;
    }
}
