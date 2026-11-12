package backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @Email(message = "Invalid email") @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address) {
}