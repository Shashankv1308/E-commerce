package com.spring.ecommerce.product;

import com.spring.ecommerce.inventory.Inventory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product 
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private boolean isActive = true;

    @OneToOne (mappedBy = "product", cascade = CascadeType.ALL, optional = false)
    private Inventory inventory;
}
