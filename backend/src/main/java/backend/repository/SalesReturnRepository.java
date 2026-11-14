package backend.repository;

import backend.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findAllByOrderByCreatedAtDesc();
    boolean existsByOrderId(Long orderId);
    @Query("select coalesce(sum(r.refundAmount), 0) from SalesReturn r")
    BigDecimal totalRefunds();

    @Query("select coalesce(sum(r.refundAmount), 0) from SalesReturn r where r.createdAt >= :from and r.createdAt < :to")
    BigDecimal refundsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}