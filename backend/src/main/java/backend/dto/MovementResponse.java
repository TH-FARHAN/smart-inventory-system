package backend.dto;

import backend.entity.StockMovement;
import java.time.LocalDateTime;

public record MovementResponse(Long id, Long productId, String productName, String sku, String type,
                               int quantity, int previousStock, int updatedStock, String reason,
                               String userName, LocalDateTime createdAt) {
    public static MovementResponse from(StockMovement m) {
        return new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getName(),
                m.getProduct().getSku(), m.getMovementType().name(), m.getQuantity(),
                m.getPreviousStock(), m.getUpdatedStock(), m.getReason(),
                m.getUser().getName(), m.getCreatedAt());
    }
}