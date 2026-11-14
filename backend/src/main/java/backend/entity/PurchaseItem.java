package backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items")
@Getter @Setter @NoArgsConstructor
public class PurchaseItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private PurchaseOrder purchaseOrder;
    @ManyToOne(optional = false)
    private Product product;
    private int quantity;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}