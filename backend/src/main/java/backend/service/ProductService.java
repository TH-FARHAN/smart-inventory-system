package backend.service;

import backend.dto.ProductRequest;
import backend.dto.ProductResponse;
import backend.entity.*;
import backend.enums.MovementType;
import backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockMovementRepository movements;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String keyword) {
        List<Product> result = (keyword == null || keyword.isBlank())
                ? products.findByActiveTrueOrderByNameAsc()
                : products.search(keyword.trim());
        return result.stream().map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest r, String email) {
        String sku = r.sku().trim().toUpperCase();
        if (products.existsBySkuIgnoreCase(sku))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        Product p = new Product();
        apply(p, r, sku);
        int opening = r.openingStock() == null ? 0 : r.openingStock();
        p.setCurrentStock(opening);
        products.save(p);
        if (opening > 0) {
            User u = users.findByEmail(email).orElseThrow();
            movements.save(StockMovement.of(p, u, MovementType.STOCK_IN, opening, 0, opening, "Opening stock"));
        }
        return ProductResponse.from(p);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = find(id);
        String sku = r.sku().trim().toUpperCase();
        if (products.existsBySkuIgnoreCaseAndIdNot(sku, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        apply(p, r, sku);
        return ProductResponse.from(p);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private void apply(Product p, ProductRequest r, String sku) {
        p.setName(r.name().trim());
        p.setSku(sku);
        p.setDescription(r.description());
        p.setPurchasePrice(r.purchasePrice());
        p.setSellingPrice(r.sellingPrice());
        p.setMinimumStock(r.minimumStock());
        p.setCategory(r.categoryId() == null ? null : categories.findById(r.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found")));
    }

    private Product find(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }
}