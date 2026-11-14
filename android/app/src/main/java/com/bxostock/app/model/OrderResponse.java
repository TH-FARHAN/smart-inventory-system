package com.bxostock.app.model;

import java.util.List;

public class OrderResponse {
    public long id;
    public long customerId;
    public String customerName;
    public String createdBy;
    public String status;
    public double totalAmount;
    public String createdAt;
    public List<OrderLine> items;
}