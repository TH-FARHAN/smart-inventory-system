package backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryReportResponse(long totalProducts, long totalUnits,
                                      BigDecimal stockValueAtCost, BigDecimal stockValueAtSelling,
                                      long lowStockCount, List<ProductResponse> products) {
}