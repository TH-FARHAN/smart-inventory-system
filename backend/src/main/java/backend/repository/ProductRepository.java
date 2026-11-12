package backend.repository;

import backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
    List<Product> findByActiveTrueOrderByNameAsc();

    @Query("select p from Product p where p.active = true and (lower(p.name) like lower(concat('%', :kw, '%')) or lower(p.sku) like lower(concat('%', :kw, '%'))) order by p.name")
    List<Product> search(@Param("kw") String kw);

    @Query("select p from Product p where p.active = true and p.currentStock <= p.minimumStock order by p.currentStock")
    List<Product> findLowStock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    
    @Query("select count(p) from Product p where p.active = true")
    long countActive();

    @Query("select coalesce(sum(p.currentStock), 0) from Product p where p.active = true")
    long totalStock();

    @Query("select count(p) from Product p where p.active = true and p.currentStock <= p.minimumStock")
    long countLowStock();
}