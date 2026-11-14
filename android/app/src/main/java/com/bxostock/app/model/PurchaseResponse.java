package com.bxostock.app.model;

import java.util.List;

public class PurchaseResponse {
    public long id;
    public long supplierId;
    public String supplierName;
    public String createdBy;
    public String status;
    public double totalCost;
    public String createdAt;
    public String receivedAt;
    public List<PurchaseLine> items;
}
