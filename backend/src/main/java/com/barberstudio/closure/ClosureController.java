package com.barberstudio.closure;

import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.closure.ClosureService.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/closures")
@RequiredArgsConstructor
public class ClosureController {
    private final ClosureService service;

    @GetMapping
    public List<ClosureResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from != null ? from : LocalDate.now().minusDays(30), to != null ? to : LocalDate.now().plusDays(400));
    }

    /** Lista los turnos que el bloqueo afectaria, sin crear nada. */
    @PostMapping("/preview")
    public List<AppointmentResponse> preview(@Valid @RequestBody ClosureRequest r) { return service.preview(r); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClosureResult create(@Valid @RequestBody ClosureRequest r) { return service.create(r); }

    @PutMapping("/{id}")
    public ClosureResult update(@PathVariable Long id, @Valid @RequestBody ClosureRequest r) { return service.update(id, r); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
