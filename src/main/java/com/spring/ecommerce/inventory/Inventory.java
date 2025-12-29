package com.spring.ecommerce.inventory;

import com.spring.ecommerce.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory 
{
    @Id
    private Long productId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    private Integer availableStock;
}
