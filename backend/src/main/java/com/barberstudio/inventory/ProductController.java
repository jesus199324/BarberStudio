package com.barberstudio.inventory;

import com.barberstudio.inventory.ProductService.MovementResponse;
import com.barberstudio.inventory.ProductService.ProductRequest;
import com.barberstudio.inventory.ProductService.ProductResponse;
import com.barberstudio.inventory.ProductService.StockAdjustRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "false") boolean lowStock) {
        return service.list(lowStock);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse create(@Valid @RequestBody ProductRequest r) { return service.create(r); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) { return service.update(id, r); }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustRequest r) {
        return service.adjustStock(id, r);
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasRole('OWNER')")
    public List<MovementResponse> movements(@PathVariable Long id) { return service.movements(id); }
}
