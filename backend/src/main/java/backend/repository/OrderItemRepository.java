package backend.repository;

import backend.dto.ProductSalesRow;
import backend.entity.OrderItem;
import backend.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select new backend.dto.ProductSalesRow(i.product.id, i.product.name, i.product.sku, sum(i.quantity), sum(i.subtotal)) " +
           "from OrderItem i where i.order.status = :status and i.order.createdAt >= :from and i.order.createdAt < :to " +
           "group by i.product.id, i.product.name, i.product.sku order by sum(i.subtotal) desc")
    List<ProductSalesRow> salesByProduct(@Param("status") OrderStatus status,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}