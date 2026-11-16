package com.bxostock.app.model;

public class Movement {
    public long id;
    public long productId;
    public String productName;
    public String sku;
    public String type;        // STOCK_IN or STOCK_OUT
    public int quantity;
    public int previousStock;
    public int updatedStock;
    public String reason;
    public String userName;
    public String createdAt;
}
