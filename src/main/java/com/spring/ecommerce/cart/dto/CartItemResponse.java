package com.spring.ecommerce.cart.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemResponse 
{
    private Long productId;
    private String productName;
    private int quantity;
    private double price;
    private double subtotal;
}
