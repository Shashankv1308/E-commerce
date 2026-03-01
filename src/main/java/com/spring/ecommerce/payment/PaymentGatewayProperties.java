package com.spring.ecommerce.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for payment gateway.
 * 
 * Usage in application.yml:
 * <pre>
 * payment:
 *   gateway:
 *     provider: razorpay  # or "mock" for testing
 *     razorpay:
 *       key-id: ${RAZORPAY_KEY_ID}
 *       key-secret: ${RAZORPAY_KEY_SECRET}
 *       webhook-secret: ${RAZORPAY_WEBHOOK_SECRET}
 *       merchant-vpa: your-vpa@razorpay
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "payment.gateway")
@Getter
@Setter
public class PaymentGatewayProperties 
{
    /**
     * Gateway provider: "razorpay" or "mock"
     */
    private String provider = "mock";

    /**
     * Payment link expiry in minutes.
     * How long the customer has to complete the payment.
     */
    private long paymentLinkExpiryMinutes = 15;

    /**
     * Stale payment threshold in minutes.
     * Payments in AWAITING_PAYMENT state beyond this are reconciled.
     */
    private long stalePaymentThresholdMinutes = 20;

    /**
     * Razorpay-specific configuration
     */
    private Razorpay razorpay = new Razorpay();

    @Getter
    @Setter
    public static class Razorpay 
    {
        private String keyId;
        private String keySecret;
        private String webhookSecret;
        private String merchantVpa;
    }
}
