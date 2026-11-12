package backend.repository;

import backend.entity.SalesOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

import backend.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findAllByOrderByCreatedAtDesc();
    List<SalesOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    
    long countByStatus(OrderStatus status);

    long countByStatusAndCreatedAtGreaterThanEqual(OrderStatus status, LocalDateTime from);

    @Query("select coalesce(sum(o.totalAmount), 0) from SalesOrder o where o.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);

    List<SalesOrder> findTop5ByOrderByCreatedAtDesc();

    
    @Query("select count(o) from SalesOrder o where o.status = :status and o.createdAt >= :from and o.createdAt < :to")
    long countBetween(@Param("status") OrderStatus status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(o.totalAmount), 0) from SalesOrder o where o.status = :status and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumBetween(@Param("status") OrderStatus status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    
}