package backend.controller;

import backend.dto.MovementResponse;
import backend.dto.ProductResponse;
import backend.dto.StockRequest;
import backend.enums.MovementType;
import backend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final StockService service;

    @PostMapping("/stock-in")
    public ProductResponse stockIn(@Valid @RequestBody StockRequest r, Authentication auth) {
        return service.change(r, MovementType.STOCK_IN, auth.getName());
    }

    @PostMapping("/stock-out")
    public ProductResponse stockOut(@Valid @RequestBody StockRequest r, Authentication auth) {
        return service.change(r, MovementType.STOCK_OUT, auth.getName());
    }

    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() { return service.lowStock(); }

    @GetMapping("/movements")
    public List<MovementResponse> movements(@RequestParam(required = false) Long productId) {
        return service.history(productId);
    }
}