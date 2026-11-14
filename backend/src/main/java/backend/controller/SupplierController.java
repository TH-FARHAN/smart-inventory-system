package backend.controller;

import backend.entity.Supplier;
import backend.repository.SupplierRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SupplierController {
    private final SupplierRepository suppliers;

    public record SupplierRequest(@NotBlank(message = "Name is required") @Size(max = 100) String name,
                                  @Email(message = "Invalid email") @Size(max = 100) String email,
                                  @Size(max = 20) String phone,
                                  @Size(max = 255) String address) {}

    public record SupplierResponse(Long id, String name, String email, String phone, String address) {
        static SupplierResponse from(Supplier s) {
            return new SupplierResponse(s.getId(), s.getName(), s.getEmail(), s.getPhone(), s.getAddress());
        }
    }

    @GetMapping
    public List<SupplierResponse> list() {
        return suppliers.findByActiveTrueOrderByNameAsc().stream().map(SupplierResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest r) {
        if (suppliers.existsByNameIgnoreCaseAndActiveTrue(r.name().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier already exists");
        Supplier s = new Supplier();
        apply(s, r);
        suppliers.save(s);
        return ResponseEntity.status(HttpStatus.CREATED).body(SupplierResponse.from(s));
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        Supplier s = suppliers.findById(id).filter(Supplier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        if (suppliers.existsByNameIgnoreCaseAndActiveTrueAndIdNot(r.name().trim(), id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier already exists");
        apply(s, r);
        suppliers.save(s);
        return SupplierResponse.from(s);
    }

    private void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name().trim());
        s.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim());
        s.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        s.setAddress(r.address() == null || r.address().isBlank() ? null : r.address().trim());
    }
}