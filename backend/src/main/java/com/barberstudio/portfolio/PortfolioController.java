package com.barberstudio.portfolio;

import com.barberstudio.portfolio.PortfolioService.ItemRequest;
import com.barberstudio.portfolio.PortfolioService.ItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService service;

    @GetMapping
    public List<ItemResponse> list(@RequestParam(required = false) PortfolioStatus status) { return service.list(status); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@Valid @RequestBody ItemRequest r) { return service.create(r); }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable Long id, @Valid @RequestBody ItemRequest r) { return service.update(id, r); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/{id}/publish")
    public ItemResponse publish(@PathVariable Long id) { return service.publish(id); }

    @PostMapping("/{id}/unpublish")
    public ItemResponse unpublish(@PathVariable Long id) { return service.unpublish(id); }

    @PostMapping("/{id}/archive")
    public ItemResponse archive(@PathVariable Long id) { return service.archive(id); }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addImage(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) String alt) {
        return service.addImage(id, file, alt);
    }

    @PutMapping("/{id}/images/{imageId}/cover")
    public ItemResponse cover(@PathVariable Long id, @PathVariable Long imageId) { return service.setCover(id, imageId); }

    @DeleteMapping("/{id}/images/{imageId}")
    public ItemResponse deleteImage(@PathVariable Long id, @PathVariable Long imageId) { return service.deleteImage(id, imageId); }
}
