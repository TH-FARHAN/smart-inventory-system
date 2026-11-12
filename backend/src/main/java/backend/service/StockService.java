package backend.service;

import backend.dto.MovementResponse;
import backend.dto.ProductResponse;
import backend.dto.StockRequest;
import backend.entity.Product;
import backend.entity.StockMovement;
import backend.entity.User;
import backend.enums.MovementType;
import backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;

    @Transactional
    public ProductResponse change(StockRequest r, MovementType type, String email) {
        if (type == MovementType.STOCK_OUT && (r.reason() == null || r.reason().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required for stock-out");
        Product p = products.findByIdForUpdate(r.productId())
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        int prev = p.getCurrentStock();
        int updated = type == MovementType.STOCK_IN ? prev + r.quantity() : prev - r.quantity();
        if (updated < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock. Available: " + prev);
        p.setCurrentStock(updated);
        User u = users.findByEmail(email).orElseThrow();
        movements.save(StockMovement.of(p, u, type, r.quantity(), prev, updated, r.reason()));
        return ProductResponse.from(p);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> lowStock() {
        return products.findLowStock().stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementResponse> history(Long productId) {
        List<StockMovement> list = productId == null
                ? movements.findTop100ByOrderByCreatedAtDesc()
                : movements.findTop100ByProductIdOrderByCreatedAtDesc(productId);
        return list.stream().map(MovementResponse::from).toList();
    }
}