package com.spring.ecommerce.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class OrderItemResponse implements Serializable 
{
    private Long productId;
    private String productName;
    private int quantity;
    private double priceAtPurchase;
    private double subtotal;
}
