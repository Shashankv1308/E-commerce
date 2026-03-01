package com.spring.ecommerce.payment.gateway;

import com.spring.ecommerce.payment.dto.PaymentInitiationRequest;
import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.dto.PaymentVerificationResult;

/**
 * Abstraction for payment gateway operations.
 * Implementations: RazorpayGateway (production), MockPaymentGateway (testing)
 */
public interface PaymentGateway 
{
    /**
     * Initiates a payment with the gateway.
     * For UPI: Creates an order and returns payment link/UPI intent.
     */
    PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request);

    /**
     * Verifies payment status using the gateway order ID.
     * Used for reconciliation of stale payments where gatewayPaymentId may not exist.
     * Fetches all payment attempts for the order and checks if any succeeded.
     */
    PaymentVerificationResult verifyPaymentByGatewayOrderId(String gatewayOrderId);

    /**
     * Verifies webhook signature to ensure authenticity.
     */
    boolean verifyWebhookSignature(String payload, String signature);
}
