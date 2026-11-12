package backend.dto;

import jakarta.validation.constraints.*;

public record StockRequest(
        @NotNull Long productId,
        @NotNull @Min(value = 1, message = "Quantity must be at least 1") Integer quantity,
        @Size(max = 255) String reason) {}