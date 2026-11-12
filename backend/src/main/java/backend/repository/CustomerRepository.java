package backend.repository;

import backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByNameAsc();

    @Query("select c from Customer c where lower(c.name) like lower(concat('%', :kw, '%')) or lower(coalesce(c.phone, '')) like lower(concat('%', :kw, '%')) or lower(coalesce(c.email, '')) like lower(concat('%', :kw, '%')) order by c.name")
    List<Customer> search(@Param("kw") String kw);
}