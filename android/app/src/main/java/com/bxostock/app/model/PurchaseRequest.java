package com.bxostock.app.model;

import java.util.ArrayList;
import java.util.List;

public class PurchaseRequest {
    public long supplierId;
    public List<Item> items = new ArrayList<>();

    public static class Item {
        public long productId;
        public int quantity;
        public double unitCost;

        public Item(long productId, int quantity, double unitCost) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitCost = unitCost;
        }
    }
}
