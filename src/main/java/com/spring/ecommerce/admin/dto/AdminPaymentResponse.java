package com.spring.ecommerce.admin.dto;

import java.time.LocalDateTime;

import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminPaymentResponse 
{
    private Long paymentId;
    private Long orderId;
    private String userEmail;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private Double amount;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String upiTransactionId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
