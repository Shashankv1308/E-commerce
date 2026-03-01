package com.spring.ecommerce.payment.dto;

import com.spring.ecommerce.order.PaymentMethod;

import lombok.Builder;
import lombok.Getter;

/**
 * Request to initiate payment with gateway.
 */
@Getter
@Builder
public class PaymentInitiationRequest 
{
    private Long orderId;
    private Long paymentId;
    private Double amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private String customerEmail;
    private String customerPhone;
    private String description;
    private String callbackUrl;
}
