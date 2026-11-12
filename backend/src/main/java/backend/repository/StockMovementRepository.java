package backend.repository;

import backend.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findTop100ByOrderByCreatedAtDesc();
    List<StockMovement> findTop100ByProductIdOrderByCreatedAtDesc(Long productId);
}