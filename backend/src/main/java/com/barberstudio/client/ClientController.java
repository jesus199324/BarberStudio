package com.barberstudio.client;

import com.barberstudio.appointment.AppointmentService.VisitResponse;
import com.barberstudio.client.ClientService.ClientRequest;
import com.barberstudio.client.ClientService.ClientResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {
    private final ClientService service;

    @GetMapping
    public List<ClientResponse> list(@RequestParam(required = false) String q) { return service.list(q); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@Valid @RequestBody ClientRequest r) { return service.create(r); }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest r) { return service.update(id, r); }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) { service.archive(id); }

    @GetMapping("/{id}/visits")
    public List<VisitResponse> visits(@PathVariable Long id) { return service.visits(id); }
}
