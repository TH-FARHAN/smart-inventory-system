package com.bxostock.app.model;

import java.util.List;

public class SalesReport {
    public String from;
    public String to;
    public long totalOrders;
    public double totalSales;
    public double averageOrderValue;
    public List<ProductSalesRow> byProduct;
}