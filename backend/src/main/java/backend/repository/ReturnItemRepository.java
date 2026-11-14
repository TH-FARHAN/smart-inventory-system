package backend.repository;

import backend.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {
    @Query("select coalesce(sum(i.quantity), 0) from ReturnItem i where i.salesReturn.order.id = :orderId and i.product.id = :productId")
    long returnedQty(@Param("orderId") Long orderId, @Param("productId") Long productId);
}