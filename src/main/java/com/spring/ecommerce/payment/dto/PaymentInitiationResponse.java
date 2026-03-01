package com.spring.ecommerce.payment.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response from gateway after payment initiation.
 * Contains links for customer to complete payment.
 */
@Getter
@Builder
public class PaymentInitiationResponse 
{
    private String gatewayOrderId;      // Gateway's order reference
    private String paymentLink;          // Short URL for payment page
    private String upiDeepLink;          // UPI intent link (upi://pay?...)
    private String qrCodeUrl;            // QR code image URL for UPI scan
    private Long expiresAt;              // Timestamp when link expires
    private boolean success;
    private String errorMessage;
}
