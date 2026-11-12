package backend.dto;

import java.math.BigDecimal;

public record ProductSalesRow(Long productId, String productName, String sku,
                              Long quantitySold, BigDecimal revenue) {
}