package com.barberstudio.catalog;

import com.barberstudio.catalog.CatalogService.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalog;

    @GetMapping("/services")
    public List<ServiceResponse> services(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return catalog.listServices(activeOnly);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(@Valid @RequestBody ServiceRequest r) { return catalog.createService(r); }

    @PutMapping("/services/{id}")
    public ServiceResponse updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest r) {
        return catalog.updateService(id, r);
    }

    @GetMapping("/styles")
    public List<StyleResponse> styles(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return catalog.listStyles(activeOnly);
    }

    @PostMapping("/styles")
    @ResponseStatus(HttpStatus.CREATED)
    public StyleResponse createStyle(@Valid @RequestBody StyleRequest r) { return catalog.createStyle(r); }

    @PutMapping("/styles/{id}")
    public StyleResponse updateStyle(@PathVariable Long id, @Valid @RequestBody StyleRequest r) {
        return catalog.updateStyle(id, r);
    }
}
