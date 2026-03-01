package com.spring.ecommerce.payment;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.event.OrderPlacedEvent;
import com.spring.ecommerce.payment.event.PaymentCompletedEvent;
import com.spring.ecommerce.payment.event.PaymentFailedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for payment-related events and triggers appropriate actions.
 * 
 * This is the central orchestrator for the payment saga:
 * 1. OrderPlacedEvent → Initiates payment with gateway (for ONLINE orders)
 * 2. PaymentCompletedEvent → Logs success (order already updated in PaymentServiceImpl)
 * 3. PaymentFailedEvent → Logs failure (compensation already done in PaymentServiceImpl)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener 
{
    private final PaymentService paymentService;

    /**
     * Triggered after order placement transaction commits.
     * Initiates payment with gateway for ONLINE payment methods.
     * 
     * Uses @Async to run in separate thread, not blocking order response.
     */
    @Async("paymentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) 
    {
        log.info("Received OrderPlacedEvent: orderId={}, paymentMethod={}", 
                event.getOrderId(), event.getPaymentMethod());

        // COD orders are already filtered out at the source (OrderServiceImpl)
        // so this listener only receives ONLINE orders

        try {
            PaymentInitiationResponse response = paymentService.initiatePayment(event.getOrderId());
            
            if (response.isSuccess()) {
                log.info("Payment initiated successfully for order {}: link={}", 
                        event.getOrderId(), response.getPaymentLink());
            } else {
                log.error("Payment initiation failed for order {}: {}", 
                        event.getOrderId(), response.getErrorMessage());
                
                // If gateway initiation fails, mark payment as failed
                paymentService.markPaymentFailed(
                        event.getOrderId(), 
                        "Gateway initiation failed: " + response.getErrorMessage()
                );
            }
        } catch (Exception e) {
            log.error("Error initiating payment for order {}: {}", 
                    event.getOrderId(), e.getMessage(), e);
            
            // Compensate on exception
            try {
                paymentService.markPaymentFailed(event.getOrderId(), "Payment initiation error: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to compensate for order {}: {}", event.getOrderId(), ex.getMessage());
            }
        }
    }

    /**
     * Logs successful payment completion.
     * Order/Payment status already updated in PaymentServiceImpl.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) 
    {
        log.info("Payment completed: orderId={}, paymentId={}, gatewayPaymentId={}", 
                event.getOrderId(), event.getPaymentId(), event.getGatewayPaymentId());
        
        // Future: Send confirmation email, trigger shipping, etc.
    }

    /**
     * Logs payment failure.
     * Compensation (inventory restoration) already done in PaymentServiceImpl.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) 
    {
        log.info("Payment failed: orderId={}, paymentId={}, reason={}", 
                event.getOrderId(), event.getPaymentId(), event.getFailureReason());
        
        // Future: Send failure notification email, etc.
    }
}
