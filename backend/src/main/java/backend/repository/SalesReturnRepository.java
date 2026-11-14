package backend.repository;

import backend.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    List<SalesReturn> findAllByOrderByCreatedAtDesc();
    boolean existsByOrderId(Long orderId);
}