package backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(long totalProducts, long totalStock, long lowStockCount,
                                long totalCustomers, long totalOrders, BigDecimal totalSales,
                                long todaysOrders, List<OrderResponse> recentOrders,
                                List<ProductResponse> lowStockProducts) {
}