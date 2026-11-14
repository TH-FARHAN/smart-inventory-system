package com.bxostock.app.model;

import java.util.ArrayList;
import java.util.List;

public class OrderRequest {
    public long customerId;
    public List<Item> items = new ArrayList<>();

    public static class Item {
        public long productId;
        public int quantity;

        public Item(long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}