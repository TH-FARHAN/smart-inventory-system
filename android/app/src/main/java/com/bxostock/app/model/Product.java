package com.bxostock.app.model;

public class Product {
    public long id;
    public String name;
    public String sku;
    public String description;
    public Long categoryId;
    public String categoryName;
    public Long supplierId;
    public String supplierName;
    public double purchasePrice;
    public double sellingPrice;
    public int currentStock;
    public int minimumStock;
    public boolean active;
    public boolean lowStock;
}