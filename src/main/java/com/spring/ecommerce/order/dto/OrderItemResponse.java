package com.spring.ecommerce.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemResponse 
{
    private Long productId;
    private String productName;
    private int quantity;
    private double priceAtPurchase;
    private double subtotal;
}
