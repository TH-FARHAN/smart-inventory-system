package backend.dto;

import backend.entity.Product;
import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String sku, String description,
                              Long categoryId, String categoryName,
                              BigDecimal purchasePrice, BigDecimal sellingPrice,
                              int currentStock, int minimumStock, boolean active, boolean lowStock) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getSku(), p.getDescription(),
                p.getCategory() == null ? null : p.getCategory().getId(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getPurchasePrice(), p.getSellingPrice(), p.getCurrentStock(),
                p.getMinimumStock(), p.isActive(), p.getCurrentStock() <= p.getMinimumStock());
    }
}