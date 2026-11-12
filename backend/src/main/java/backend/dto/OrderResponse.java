package backend.dto;

import backend.entity.SalesOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, Long customerId, String customerName, String createdBy,
                            String status, BigDecimal totalAmount, LocalDateTime createdAt,
                            List<Line> items) {

    public record Line(Long productId, String productName, String sku, int quantity,
                       BigDecimal unitPrice, BigDecimal subtotal) {
    }

    public static OrderResponse from(SalesOrder o) {
        List<Line> lines = o.getItems().stream()
                .map(i -> new Line(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getSku(),
                        i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderResponse(o.getId(), o.getCustomer().getId(), o.getCustomer().getName(),
                o.getCreatedBy().getEmail(), o.getStatus().name(), o.getTotalAmount(),
                o.getCreatedAt(), lines);
    }
}