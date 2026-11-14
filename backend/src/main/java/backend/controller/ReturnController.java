package backend.controller;

import backend.service.AuditService;
import backend.service.ReturnService;
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
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class ReturnController {
    private final ReturnService service;
    private final AuditService audit;

    @GetMapping
    public List<ReturnService.ReturnResponse> list() { return service.list(); }

    @GetMapping("/order/{orderId}/returnable")
    public List<ReturnService.Returnable> returnable(@PathVariable Long orderId) {
        return service.returnable(orderId);
    }

    @PostMapping
    public ResponseEntity<ReturnService.ReturnResponse> create(@Valid @RequestBody ReturnService.ReturnRequest r,
                                                               Authentication auth, HttpServletRequest http) {
        ReturnService.ReturnResponse res = service.create(r, auth.getName());
        try {
            audit.log(auth.getName(), "RETURN_CREATED",
                    "Return #" + res.id() + " for order #" + res.orderId() + ", refund " + res.refundAmount(),
                    http.getRemoteAddr());
        } catch (Exception ignored) {
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}