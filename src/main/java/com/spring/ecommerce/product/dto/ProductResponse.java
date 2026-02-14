package com.spring.ecommerce.product.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponse
{
    private Long id;
    private String name;
    private String description;
    private double price;
    private int availableStock;
}
