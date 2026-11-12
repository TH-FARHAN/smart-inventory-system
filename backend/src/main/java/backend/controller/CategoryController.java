package backend.controller;

import backend.entity.Category;
import backend.repository.CategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    public record CategoryRequest(@NotBlank String name, String description) {}

    private final CategoryRepository categories;

    @GetMapping
    public List<Category> list() { return categories.findAll(); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> create(@Valid @RequestBody CategoryRequest r) {
        if (categories.existsByNameIgnoreCase(r.name().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists");
        Category c = new Category();
        c.setName(r.name().trim());
        c.setDescription(r.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(categories.save(c));
    }
}