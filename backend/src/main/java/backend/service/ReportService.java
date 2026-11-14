package backend.service;

import backend.dto.InventoryReportResponse;
import backend.dto.ProductResponse;
import backend.dto.SalesReportResponse;
import backend.entity.Product;
import backend.enums.OrderStatus;
import backend.repository.OrderItemRepository;
import backend.repository.ProductRepository;
import backend.repository.SalesOrderRepository;
import backend.repository.SalesReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final SalesOrderRepository orders;
    private final SalesReturnRepository returnsRepo;
    private final OrderItemRepository items;
    private final ProductRepository products;

    @Transactional(readOnly = true)
    public SalesReportResponse sales(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' date must not be after 'to' date");

        var fromTime = start.atStartOfDay();
        var toTime = end.plusDays(1).atStartOfDay();
        long count = orders.countBetween(OrderStatus.CONFIRMED, fromTime, toTime);
        BigDecimal gross = orders.sumBetween(OrderStatus.CONFIRMED, fromTime, toTime);
        BigDecimal total = gross.subtract(returnsRepo.refundsBetween(fromTime, toTime));
        BigDecimal average = count == 0 ? BigDecimal.ZERO
                : gross.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new SalesReportResponse(start, end, count, total, average,
                items.salesByProduct(OrderStatus.CONFIRMED, fromTime, toTime));
    }

    @Transactional(readOnly = true)
    public InventoryReportResponse inventory() {
        List<Product> list = products.findByActiveTrueOrderByNameAsc();
        long units = 0;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal selling = BigDecimal.ZERO;
        long low = 0;
        for (Product p : list) {
            BigDecimal qty = BigDecimal.valueOf(p.getCurrentStock());
            units += p.getCurrentStock();
            cost = cost.add(p.getPurchasePrice().multiply(qty));
            selling = selling.add(p.getSellingPrice().multiply(qty));
            if (p.getCurrentStock() <= p.getMinimumStock()) low++;
        }
        return new InventoryReportResponse(list.size(), units, cost, selling, low,
                list.stream().map(ProductResponse::from).toList());
    }
}