package com.spring.ecommerce.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class ProductResponse implements Serializable
{
    private Long id;
    private String name;
    private String description;
    private double price;
    private int availableStock;
}
