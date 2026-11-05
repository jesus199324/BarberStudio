package com.barberstudio.barber;

import com.barberstudio.barber.BarberAdminService.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/barbers")
@RequiredArgsConstructor
public class BarberController {
    private final BarberAdminService service;

    @GetMapping
    public List<BarberResponse> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarberResponse create(@Valid @RequestBody BarberRequest r) { return service.create(r); }

    @PutMapping("/{id}")
    public BarberResponse update(@PathVariable Long id, @Valid @RequestBody BarberRequest r) { return service.update(id, r); }

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    public BarberResponse uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        try { return service.uploadPhoto(id, file.getBytes()); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @DeleteMapping("/{id}/photo")
    public BarberResponse deletePhoto(@PathVariable Long id) { return service.deletePhoto(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/{id}/hours")
    public List<HoursItem> hours(@PathVariable Long id) { return service.hours(id); }

    @PutMapping("/{id}/hours")
    public List<HoursItem> setHours(@PathVariable Long id, @Valid @RequestBody HoursRequest r) { return service.setHours(id, r.items()); }

    @GetMapping("/{id}/overrides")
    public List<OverrideResponse> overrides(@PathVariable Long id) { return service.overrides(id); }

    @PostMapping("/{id}/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    public OverrideResponse addOverride(@PathVariable Long id, @Valid @RequestBody OverrideRequest r) { return service.addOverride(id, r); }

    @DeleteMapping("/{id}/overrides/{overrideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOverride(@PathVariable Long id, @PathVariable Long overrideId) { service.deleteOverride(id, overrideId); }
}
