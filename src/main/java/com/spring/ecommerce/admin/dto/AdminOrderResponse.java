package com.spring.ecommerce.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.order.dto.OrderItemResponse;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminOrderResponse 
{
    private Long orderId;
    private Long userId;
    private String userEmail;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private Double totalAmount;
    private LocalDateTime createdAt;
    private String gatewayOrderId;
    private List<OrderItemResponse> items;
}
