package backend.service;

import backend.dto.OrderRequest;
import backend.dto.OrderResponse;
import backend.entity.*;
import backend.enums.MovementType;
import backend.enums.OrderStatus;
import backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final SalesOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;

    @Transactional
    public OrderResponse create(OrderRequest r, String email) {
        Customer customer = customers.findById(r.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        User user = users.findByEmail(email).orElseThrow();

        // merge duplicate products and sort by id so row locks are always taken in the same order
        Map<Long, Integer> wanted = new TreeMap<>();
        for (OrderRequest.Item i : r.items()) {
            wanted.merge(i.productId(), i.quantity(), Integer::sum);
        }

        SalesOrder order = new SalesOrder();
        order.setCustomer(customer);
        order.setCreatedBy(user);
        orders.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            int qty = e.getValue();
            Product p = products.findByIdForUpdate(e.getKey())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Product not found: " + e.getKey()));
            int prev = p.getCurrentStock();
            if (prev < qty)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Insufficient stock for " + p.getName() + ". Available: " + prev);
            p.setCurrentStock(prev - qty);

            BigDecimal unitPrice = p.getSellingPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(p);
            item.setQuantity(qty);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(subtotal);
            order.getItems().add(item);
            total = total.add(subtotal);

            movements.save(StockMovement.of(p, user, MovementType.STOCK_OUT, qty, prev, prev - qty,
                    "Order #" + order.getId()));
        }
        order.setTotalAmount(total);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(Long id, String email) {
        SalesOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.getStatus() == OrderStatus.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already cancelled");
        User user = users.findByEmail(email).orElseThrow();

        for (OrderItem item : order.getItems()) {
            Product p = products.findByIdForUpdate(item.getProduct().getId()).orElseThrow();
            int prev = p.getCurrentStock();
            int updated = prev + item.getQuantity();
            p.setCurrentStock(updated);
            movements.save(StockMovement.of(p, user, MovementType.STOCK_IN, item.getQuantity(), prev, updated,
                    "Order #" + order.getId() + " cancelled"));
        }
        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return OrderResponse.from(orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }
}