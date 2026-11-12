package backend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String name,
        @NotBlank String sku,
        String description,
        Long categoryId,
        @NotNull @DecimalMin("0.0") BigDecimal purchasePrice,
        @NotNull @DecimalMin("0.0") BigDecimal sellingPrice,
        @Min(0) int minimumStock,
        @Min(0) Integer openingStock) {}