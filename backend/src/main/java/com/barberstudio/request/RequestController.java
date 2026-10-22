package com.barberstudio.request;

import com.barberstudio.appointment.AppointmentService.AppointmentResponse;
import com.barberstudio.request.RequestService.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {
    private final RequestService service;

    @GetMapping
    public List<RequestResponse> list(@RequestParam(required = false) RequestStatus status) { return service.list(status); }

    @PostMapping("/{id}/accept")
    public AppointmentResponse accept(@PathVariable Long id, @Valid @RequestBody AcceptRequest r) { return service.accept(id, r); }

    @PostMapping("/{id}/reject")
    public RequestResponse reject(@PathVariable Long id) { return service.reject(id); }
}
