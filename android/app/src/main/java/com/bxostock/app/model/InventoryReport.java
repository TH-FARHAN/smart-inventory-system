package com.bxostock.app.model;

import java.util.List;

public class InventoryReport {
    public long totalProducts;
    public long totalUnits;
    public double stockValueAtCost;
    public double stockValueAtSelling;
    public long lowStockCount;
    public List<Product> products;
}