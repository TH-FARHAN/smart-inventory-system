package com.bxostock.app.model;

import java.util.List;

public class ReturnResponse {
    public long id;
    public long orderId;
    public String customerName;
    public String createdBy;
    public String reason;
    public double refundAmount;
    public String createdAt;
    public List<ReturnLine> items;
}
