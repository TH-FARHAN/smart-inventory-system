package backend.dto;

import backend.enums.Role;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotNull Role role) {}