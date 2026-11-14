package backend.controller;

import backend.service.AuditService;
import backend.service.PurchaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PurchaseController {
    private final PurchaseService service;
    private final AuditService audit;

    @GetMapping
    public List<PurchaseService.PurchaseResponse> list() { return service.list(); }

    @PostMapping
    public ResponseEntity<PurchaseService.PurchaseResponse> create(@Valid @RequestBody PurchaseService.PurchaseRequest r,
                                                                   Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r, auth.getName()));
    }

    @PostMapping("/{id}/receive")
    public PurchaseService.PurchaseResponse receive(@PathVariable Long id, Authentication auth,
                                                    HttpServletRequest http) {
        PurchaseService.PurchaseResponse res = service.receive(id, auth.getName());
        try {
            audit.log(auth.getName(), "PURCHASE_RECEIVED",
                    "Received purchase #" + id + " from " + res.supplierName(), http.getRemoteAddr());
        } catch (Exception ignored) {
        }
        return res;
    }

    @PostMapping("/{id}/cancel")
    public PurchaseService.PurchaseResponse cancel(@PathVariable Long id) { return service.cancel(id); }
}