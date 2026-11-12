package backend.dto;

import backend.entity.Customer;

public record CustomerResponse(Long id, String name, String email, String phone, String address) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getAddress());
    }
}