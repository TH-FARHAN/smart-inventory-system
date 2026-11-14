package com.bxostock.app.model;

import java.util.List;

public class DashboardResponse {
    public long totalProducts;
    public long totalStock;
    public long lowStockCount;
    public long totalCustomers;
    public long totalOrders;
    public double totalSales;
    public long todaysOrders;
    public List<OrderResponse> recentOrders;
    public List<Product> lowStockProducts;
}