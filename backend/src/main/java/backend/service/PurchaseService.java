package backend.service;

import backend.entity.*;
import backend.enums.MovementType;
import backend.enums.PurchaseStatus;
import backend.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseService {
    private final PurchaseOrderRepository purchases;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;

    public record ItemReq(@NotNull(message = "Product is required") Long productId,
                          @Min(value = 1, message = "Quantity must be at least 1") int quantity,
                          @NotNull(message = "Unit cost is required") @DecimalMin("0.0") BigDecimal unitCost) {}

    public record PurchaseRequest(@NotNull(message = "Supplier is required") Long supplierId,
                                  @NotEmpty(message = "Add at least one product") @Valid List<ItemReq> items) {}

    public record Line(Long productId, String productName, String sku, int quantity,
                       BigDecimal unitCost, BigDecimal subtotal) {}

    public record PurchaseResponse(Long id, Long supplierId, String supplierName, String createdBy, String status,
                                   BigDecimal totalCost, LocalDateTime createdAt, LocalDateTime receivedAt,
                                   List<Line> items) {
        public static PurchaseResponse from(PurchaseOrder o) {
            List<Line> lines = o.getItems().stream()
                    .map(i -> new Line(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                            i.getQuantity(), i.getUnitCost(), i.getSubtotal()))
                    .toList();
            return new PurchaseResponse(o.getId(), o.getSupplier().getId(), o.getSupplier().getName(),
                    o.getCreatedBy().getEmail(), o.getStatus().name(), o.getTotalCost(), o.getCreatedAt(),
                    o.getReceivedAt(), lines);
        }
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> list() {
        return purchases.findAllByOrderByCreatedAtDesc().stream().map(PurchaseResponse::from).toList();
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest r, String email) {
        Supplier s = suppliers.findById(r.supplierId()).filter(Supplier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        User user = users.findByEmail(email).orElseThrow();
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplier(s);
        po.setCreatedBy(user);
        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemReq i : r.items()) {
            if (!seen.add(i.productId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The same product is listed twice");
            Product p = products.findById(i.productId()).filter(Product::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Product not found: " + i.productId()));
            if (p.getSupplier() == null || !p.getSupplier().getId().equals(s.getId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        p.getName() + " is not supplied by " + s.getName());
            BigDecimal sub = i.unitCost().multiply(BigDecimal.valueOf(i.quantity()));
            PurchaseItem pi = new PurchaseItem();
            pi.setPurchaseOrder(po);
            pi.setProduct(p);
            pi.setQuantity(i.quantity());
            pi.setUnitCost(i.unitCost());
            pi.setSubtotal(sub);
            po.getItems().add(pi);
            total = total.add(sub);
        }
        po.setTotalCost(total);
        purchases.save(po);
        return PurchaseResponse.from(po);
    }

    @Transactional
    public PurchaseResponse receive(Long id, String email) {
        PurchaseOrder po = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        if (po.getStatus() != PurchaseStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This purchase order is already " + po.getStatus());
        User user = users.findByEmail(email).orElseThrow();
        for (PurchaseItem i : po.getItems()) {
            Product p = products.findByIdForUpdate(i.getProduct().getId()).orElseThrow();
            int prev = p.getCurrentStock();
            p.setCurrentStock(prev + i.getQuantity());
            movements.save(StockMovement.of(p, user, MovementType.STOCK_IN, i.getQuantity(), prev,
                    prev + i.getQuantity(), "Purchase #" + po.getId() + " from " + po.getSupplier().getName()));
        }
        po.setStatus(PurchaseStatus.RECEIVED);
        po.setReceivedAt(LocalDateTime.now());
        return PurchaseResponse.from(po);
    }

    @Transactional
    public PurchaseResponse cancel(Long id) {
        PurchaseOrder po = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase order not found"));
        if (po.getStatus() != PurchaseStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending purchase order can be cancelled");
        po.setStatus(PurchaseStatus.CANCELLED);
        return PurchaseResponse.from(po);
    }
}