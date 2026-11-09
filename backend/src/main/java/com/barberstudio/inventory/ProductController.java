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
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse setImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return service.setImage(id, file);
    }

    @DeleteMapping("/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void removeImage(@PathVariable Long id) { service.removeImage(id); }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasRole('OWNER')")
    public List<MovementResponse> movements(@PathVariable Long id) { return service.movements(id); }
}
