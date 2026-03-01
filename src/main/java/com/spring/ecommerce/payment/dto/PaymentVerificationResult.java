package com.spring.ecommerce.payment.dto;

import com.spring.ecommerce.order.PaymentStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of payment verification with gateway.
 */
@Getter
@Builder
public class PaymentVerificationResult 
{
    private String gatewayPaymentId;
    private String gatewayOrderId;
    private PaymentStatus status;
    private Double amount;
    private String upiTransactionId;
    private String failureReason;
    private boolean verified;
}
