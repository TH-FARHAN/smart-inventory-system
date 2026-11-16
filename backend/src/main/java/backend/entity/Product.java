package backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String sku;
    private String description;
    @ManyToOne
    private Category category;
    @ManyToOne
    private Supplier supplier;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;
    private int currentStock;
    private int minimumStock;
    private boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() { this.updatedAt = LocalDateTime.now(); }
}