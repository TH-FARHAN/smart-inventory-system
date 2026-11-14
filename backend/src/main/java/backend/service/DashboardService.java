package backend.service;

import backend.dto.DashboardResponse;
import backend.dto.OrderResponse;
import backend.dto.ProductResponse;
import backend.enums.OrderStatus;
import backend.repository.CustomerRepository;
import backend.repository.ProductRepository;
import backend.repository.SalesOrderRepository;
import backend.repository.SalesReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SalesOrderRepository orders;
    private final SalesReturnRepository returnsRepo;

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        return new DashboardResponse(
                products.countActive(),
                products.totalStock(),
                products.countLowStock(),
                customers.count(),
                orders.countByStatus(OrderStatus.CONFIRMED),
                orders.sumTotalByStatus(OrderStatus.CONFIRMED).subtract(returnsRepo.totalRefunds()),
                orders.countByStatusAndCreatedAtGreaterThanEqual(OrderStatus.CONFIRMED,
                        LocalDate.now().atStartOfDay()),
                orders.findTop5ByOrderByCreatedAtDesc().stream().map(OrderResponse::from).toList(),
                products.findLowStock().stream().map(ProductResponse::from).toList());
    }
}