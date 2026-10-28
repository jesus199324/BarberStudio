package com.barberstudio.client;

import com.barberstudio.appointment.AppointmentService.VisitResponse;
import com.barberstudio.common.error.ConflictException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.common.security.CurrentUser;
import com.barberstudio.common.util.Phone;
import com.barberstudio.visit.VisitRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clients;
    private final VisitRepository visits;

    public record ClientRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String name,
        @NotBlank(message = "El WhatsApp es obligatorio") String whatsapp,
        @Size(max = 2000) String preferences,
        @Size(max = 2000) String notes,
        LocalDate birthDate,
        boolean marketingOptIn) {}

    public record ClientResponse(Long id, String name, String whatsapp, String preferences, String notes,
                                 boolean archived, Instant createdAt, LocalDate birthDate, boolean marketingOptIn) {
        public static ClientResponse from(Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getWhatsappE164(), c.getPreferences(),
                c.getNotes(), c.getArchivedAt() != null, c.getCreatedAt(), c.getBirthDate(), c.isMarketingOptIn());
        }
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(String q) {
        long shop = CurrentUser.shopId();
        Pageable page = PageRequest.of(0, 100);
        List<Client> result;
        if (q == null || q.isBlank()) {
            result = clients.findActive(shop, page);
        } else {
            String text = q.trim().toLowerCase(Locale.ROOT);
            String digits = text.replaceAll("\\D", "");
            result = clients.search(shop, "%" + text + "%", digits.isEmpty() ? "no-phone" : "%" + digits + "%", page);
        }
        return result.stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) { return ClientResponse.from(find(id)); }

    @Transactional
    public ClientResponse create(ClientRequest r) {
        long shop = CurrentUser.shopId();
        String phone = Phone.normalize(r.whatsapp());
        clients.findByBarbershopIdAndWhatsappE164AndArchivedAtIsNull(shop, phone).ifPresent(e -> {
            throw new ConflictException("Ya existe un cliente con ese WhatsApp.", Map.of("existingClientId", e.getId()));
        });
        Client c = new Client();
        c.setBarbershopId(shop);
        apply(c, r, phone);
        return ClientResponse.from(clients.saveAndFlush(c));
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest r) {
        Client c = find(id);
        String phone = Phone.normalize(r.whatsapp());
        clients.findByBarbershopIdAndWhatsappE164AndArchivedAtIsNull(c.getBarbershopId(), phone)
            .filter(e -> !e.getId().equals(id)).ifPresent(e -> {
                throw new ConflictException("Ya existe otro cliente con ese WhatsApp.", Map.of("existingClientId", e.getId()));
            });
        apply(c, r, phone);
        return ClientResponse.from(clients.saveAndFlush(c));
    }

    @Transactional
    public void archive(Long id) {
        Client c = find(id);
        if (c.getArchivedAt() == null) c.setArchivedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> visits(Long clientId) {
        Client c = find(clientId);
        return visits.findByClientIdAndBarbershopIdOrderByPerformedAtDesc(c.getId(), c.getBarbershopId())
            .stream().map(VisitResponse::from).toList();
    }

    private Client find(Long id) {
        return clients.findByIdAndBarbershopId(id, CurrentUser.shopId())
            .orElseThrow(() -> new NotFoundException("Cliente no encontrado."));
    }

    private void apply(Client c, ClientRequest r, String phone) {
        c.setName(r.name().trim());
        c.setWhatsappE164(phone);
        c.setPreferences(r.preferences());
        c.setNotes(r.notes());
        c.setBirthDate(r.birthDate());
        // La autorizacion para recibir mensajes queda registrada con su fecha; se puede revocar.
        if (r.marketingOptIn() && !c.isMarketingOptIn()) { c.setMarketingOptIn(true); c.setMarketingOptInAt(Instant.now()); }
        if (!r.marketingOptIn() && c.isMarketingOptIn()) { c.setMarketingOptIn(false); c.setMarketingOptInAt(null); }
    }
}
