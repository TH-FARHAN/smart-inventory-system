package backend.entity;

import backend.enums.MovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements")
@Getter @Setter @NoArgsConstructor
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Product product;
    @ManyToOne(optional = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType movementType;
    private int quantity;
    private int previousStock;
    private int updatedStock;
    private String reason;
    private LocalDateTime createdAt = LocalDateTime.now();

    public static StockMovement of(Product p, User u, MovementType t, int qty, int prev, int updated, String reason) {
        StockMovement m = new StockMovement();
        m.product = p; m.user = u; m.movementType = t;
        m.quantity = qty; m.previousStock = prev; m.updatedStock = updated; m.reason = reason;
        return m;
    }
}