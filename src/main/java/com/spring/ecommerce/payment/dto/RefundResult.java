package com.spring.ecommerce.payment.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of refund operation with gateway.
 */
@Getter
@Builder
public class RefundResult 
{
    private String refundId;
    private String gatewayPaymentId;
    private Double amount;
    private boolean success;
    private String errorMessage;
}
