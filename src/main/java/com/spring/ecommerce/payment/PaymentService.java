package com.spring.ecommerce.payment;

import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.dto.RazorpayWebhookPayload;

/**
 * Service for handling payment operations.
 */
public interface PaymentService 
{
    /**
     * Initiates payment with the gateway for an existing order.
     * Called asynchronously after order placement.
     * 
     * @param orderId The order ID
     * @return Payment initiation response with payment link
     */
    PaymentInitiationResponse initiatePayment(Long orderId);

    /**
     * Processes webhook callback from payment gateway.
     * Updates payment and order status, triggers compensation if needed.
     * 
     * @param payload The webhook payload
     * @param signature The webhook signature for verification
     */
    void handleWebhookCallback(RazorpayWebhookPayload payload, String signature, String rawPayload);

    /**
     * Marks payment and order as successful.
     * Called when gateway confirms payment.
     * 
     * @param orderId The order ID
     * @param gatewayPaymentId Gateway's payment reference
     * @param upiTransactionId UPI transaction reference (optional)
     */
    void markPaymentSuccess(Long orderId, String gatewayPaymentId, String upiTransactionId);

    /**
     * Marks payment as failed and triggers saga compensation.
     * Restores inventory and cancels order.
     * 
     * @param orderId The order ID
     * @param failureReason Reason for failure
     */
    void markPaymentFailed(Long orderId, String failureReason);
}
