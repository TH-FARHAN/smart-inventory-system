package backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReportResponse(LocalDate from, LocalDate to, long totalOrders,
                                  BigDecimal totalSales, BigDecimal averageOrderValue,
                                  List<ProductSalesRow> byProduct) {
}