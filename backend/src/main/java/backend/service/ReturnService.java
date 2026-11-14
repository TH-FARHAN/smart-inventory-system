package backend.service;

import backend.entity.*;
import backend.enums.MovementType;
import backend.enums.OrderStatus;
import backend.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReturnService {
    private final SalesOrderRepository orders;
    private final SalesReturnRepository returns;
    private final ReturnItemRepository returnItems;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;

    public record ItemReq(@NotNull(message = "Product is required") Long productId,
                          @Min(value = 1, message = "Quantity must be at least 1") int quantity) {}

    public record ReturnRequest(@NotNull(message = "Order is required") Long orderId,
                                @NotBlank(message = "Reason is required") @Size(max = 255) String reason,
                                @NotEmpty(message = "Choose at least one item") @Valid List<ItemReq> items) {}

    public record Line(Long productId, String productName, String sku, int quantity,
                       BigDecimal unitPrice, BigDecimal subtotal) {}

    public record ReturnResponse(Long id, Long orderId, String customerName, String createdBy, String reason,
                                 BigDecimal refundAmount, LocalDateTime createdAt, List<Line> items) {
        public static ReturnResponse from(SalesReturn r) {
            List<Line> lines = r.getItems().stream()
                    .map(i -> new Line(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                            i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                    .toList();
            return new ReturnResponse(r.getId(), r.getOrder().getId(), r.getOrder().getCustomer().getName(),
                    r.getCreatedBy().getEmail(), r.getReason(), r.getRefundAmount(), r.getCreatedAt(), lines);
        }
    }

    public record Returnable(Long productId, String productName, String sku, BigDecimal unitPrice,
                             int bought, int returned, int remaining) {}

    @Transactional(readOnly = true)
    public List<ReturnResponse> list() {
        return returns.findAllByOrderByCreatedAtDesc().stream().map(ReturnResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Returnable> returnable(Long orderId) {
        SalesOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        List<Returnable> out = new ArrayList<>();
        if (order.getStatus() == OrderStatus.CANCELLED) return out;
        for (OrderItem i : order.getItems()) {
            int returned = (int) returnItems.returnedQty(orderId, i.getProduct().getId());
            out.add(new Returnable(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                    i.getUnitPrice(), i.getQuantity(), returned, i.getQuantity() - returned));
        }
        return out;
    }

    @Transactional
    public ReturnResponse create(ReturnRequest r, String email) {
        SalesOrder order = orders.findByIdForUpdate(r.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cancelled order cannot be returned");
        User user = users.findByEmail(email).orElseThrow();

        Map<Long, Integer> wanted = new TreeMap<>();
        for (ItemReq i : r.items()) wanted.merge(i.productId(), i.quantity(), Integer::sum);

        SalesReturn sr = new SalesReturn();
        sr.setOrder(order);
        sr.setCreatedBy(user);
        sr.setReason(r.reason().trim());

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            final Long pid = e.getKey();
            int qty = e.getValue();
            OrderItem line = order.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(pid))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product " + pid + " was not in this order"));
            int already = (int) returnItems.returnedQty(order.getId(), pid);
            int remaining = line.getQuantity() - already;
            if (qty > remaining)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot return " + qty + " of " + line.getProduct().getName()
                                + ". Only " + remaining + " left to return");

            Product p = products.findByIdForUpdate(pid).orElseThrow();
            int prev = p.getCurrentStock();
            p.setCurrentStock(prev + qty);
            movements.save(StockMovement.of(p, user, MovementType.STOCK_IN, qty, prev, prev + qty,
                    "Return for order #" + order.getId()));

            BigDecimal sub = line.getUnitPrice().multiply(BigDecimal.valueOf(qty));
            ReturnItem ri = new ReturnItem();
            ri.setSalesReturn(sr);
            ri.setProduct(p);
            ri.setQuantity(qty);
            ri.setUnitPrice(line.getUnitPrice());
            ri.setSubtotal(sub);
            sr.getItems().add(ri);
            total = total.add(sub);
        }
        sr.setRefundAmount(total);
        returns.save(sr);
        return ReturnResponse.from(sr);
    }
}