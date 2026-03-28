package com.spring.ecommerce.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class OrderResponse implements Serializable 
{
    private Long orderId;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private double totalAmount;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
}
