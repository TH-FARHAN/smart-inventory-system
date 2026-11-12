package backend.service;

import backend.dto.CustomerRequest;
import backend.dto.CustomerResponse;
import backend.entity.Customer;
import backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customers;

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(String keyword) {
        List<Customer> result = (keyword == null || keyword.isBlank())
                ? customers.findAllByOrderByNameAsc()
                : customers.search(keyword.trim());
        return result.stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest r) {
        Customer c = new Customer();
        apply(c, r);
        return CustomerResponse.from(customers.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest r) {
        Customer c = find(id);
        apply(c, r);
        return CustomerResponse.from(c);
    }

    private void apply(Customer c, CustomerRequest r) {
        c.setName(r.name().trim());
        c.setEmail(r.email());
        c.setPhone(r.phone());
        c.setAddress(r.address());
    }

    private Customer find(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }
}