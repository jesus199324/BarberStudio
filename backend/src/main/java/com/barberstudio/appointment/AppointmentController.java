package com.barberstudio.appointment;

import com.barberstudio.appointment.AppointmentService.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService service;

    /** Agenda por rango de dias (inclusive) en la zona horaria de la barberia; barberId opcional. */
    @GetMapping
    public List<AppointmentResponse> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) Long barberId) {
        return service.list(from, to, barberId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody CreateRequest r) { return service.create(r); }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public AppointmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest r) { return service.update(id, r); }

    @PostMapping("/{id}/reassign")
    public AppointmentResponse reassign(@PathVariable Long id, @Valid @RequestBody ReassignRequest r) {
        return service.reassign(id, r.barberId());
    }

    @PostMapping("/{id}/confirm")
    public AppointmentResponse confirm(@PathVariable Long id) { return service.confirm(id); }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id) { return service.cancel(id); }

    @PostMapping("/{id}/complete")
    public VisitResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRequest r) {
        return service.complete(id, r);
    }

    @GetMapping("/{id}/visit")
    public VisitResponse visit(@PathVariable Long id) { return service.visitOf(id); }
}
