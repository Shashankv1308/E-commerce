package com.spring.ecommerce.payment;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.order.Order;
import com.spring.ecommerce.order.OrderItem;
import com.spring.ecommerce.order.OrderRepository;
import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.payment.dto.PaymentInitiationRequest;
import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.dto.PaymentVerificationResult;
import com.spring.ecommerce.payment.dto.RazorpayWebhookPayload;
import com.spring.ecommerce.payment.event.PaymentCompletedEvent;
import com.spring.ecommerce.payment.event.PaymentFailedEvent;
import com.spring.ecommerce.payment.gateway.PaymentGateway;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService 
{
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PaymentInitiationResponse initiatePayment(Long orderId) 
    {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        // Skip if already processed or COD
        if (payment.getPaymentStatus() != PaymentStatus.AWAITING_PAYMENT) {
            log.info("Payment {} already in state {}, skipping initiation", 
                    payment.getId(), payment.getPaymentStatus());
            return PaymentInitiationResponse.builder()
                    .success(true)
                    .gatewayOrderId(payment.getGatewayOrderId())
                    .paymentLink(payment.getPaymentLink())
                    .build();
        }

        Order order = payment.getOrder();
        User user = order.getUser();

        // Build request for gateway
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .orderId(orderId)
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .currency("INR")
                .paymentMethod(payment.getPaymentMethod())
                .customerEmail(user.getEmail())
                .customerPhone(user.getPhoneNumber())
                .description("Order #" + orderId)
                .build();

        // Call gateway
        PaymentInitiationResponse response = paymentGateway.initiatePayment(request);

        if (response.isSuccess()) {
            // Update payment with gateway references
            payment.setGatewayOrderId(response.getGatewayOrderId());
            payment.setPaymentLink(response.getPaymentLink());
            paymentRepository.save(payment);

            log.info("Payment initiated for order {}: gatewayOrderId={}, link={}", 
                    orderId, response.getGatewayOrderId(), response.getPaymentLink());
        } else {
            log.error("Payment initiation failed for order {}: {}", 
                    orderId, response.getErrorMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public void handleWebhookCallback(RazorpayWebhookPayload payload, String signature, String rawPayload) 
    {
        // Verify webhook signature
        if (!paymentGateway.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Invalid webhook signature");
            throw new BusinessException("Invalid webhook signature");
        }

        String event = payload.getEvent();
        log.info("Processing webhook event: {}", event);

        switch (event) {
            case "payment.captured", "payment.authorized" -> handlePaymentSuccess(payload);
            case "payment.failed" -> handlePaymentFailure(payload);
            default -> log.info("Ignoring webhook event: {}", event);
        }
    }

    private void handlePaymentSuccess(RazorpayWebhookPayload payload) 
    {
        var entity = payload.getPayload().getPayment().getEntity();
        String gatewayOrderId = entity.getOrderId();
        String gatewayPaymentId = entity.getId();
        
        String upiTxnId = null;
        if (entity.getAcquirerData() != null) {
            upiTxnId = entity.getAcquirerData().getUpiTransactionId();
        }

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> {
                    log.error("Payment not found for gateway order: {}", gatewayOrderId);
                    return new ResourceNotFoundException("Payment not found");
                });

        markPaymentSuccess(payment.getOrder().getId(), gatewayPaymentId, upiTxnId);
    }

    private void handlePaymentFailure(RazorpayWebhookPayload payload) 
    {
        var entity = payload.getPayload().getPayment().getEntity();
        String gatewayOrderId = entity.getOrderId();
        String failureReason = entity.getErrorDescription() != null 
                ? entity.getErrorDescription() 
                : "Payment failed";

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> {
                    log.error("Payment not found for gateway order: {}", gatewayOrderId);
                    return new ResourceNotFoundException("Payment not found");
                });

        markPaymentFailed(payment.getOrder().getId(), failureReason);
    }

    @Override
    @Transactional
    public void markPaymentSuccess(Long orderId, String gatewayPaymentId, String upiTransactionId) 
    {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        // Idempotency: skip if already processed
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment {} already marked as SUCCESS", payment.getId());
            return;
        }

        // Update payment
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setUpiTransactionId(upiTransactionId);
        paymentRepository.save(payment);

        // Update order
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        orderRepository.save(order);

        log.info("Payment SUCCESS for order {}: gatewayPaymentId={}, upiTxnId={}", 
                orderId, gatewayPaymentId, upiTransactionId);

        // Publish event for any downstream processing
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                orderId,
                payment.getId(),
                gatewayPaymentId,
                payment.getGatewayOrderId()
        ));
    }

    @Override
    @Transactional
    public void markPaymentFailed(Long orderId, String failureReason) 
    {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        // Idempotency: skip if already failed/cancelled
        if (payment.getPaymentStatus() == PaymentStatus.FAILED 
                || order.getOrderStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} already cancelled or payment failed", orderId);
            return;
        }

        // SAGA COMPENSATION: Restore inventory
        log.info("Starting saga compensation for order {}", orderId);
        for (OrderItem item : order.getItems()) {
            int updated = productRepository.increaseStock(
                    item.getProduct().getId(),
                    item.getQuantity()
            );
            if (updated > 0) {
                log.info("Restored {} units of product {} for order {}", 
                        item.getQuantity(), item.getProduct().getId(), orderId);
            }
        }

        // Update payment
        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);

        // Update order
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Payment FAILED for order {}: reason={}", orderId, failureReason);

        // Publish event
        eventPublisher.publishEvent(new PaymentFailedEvent(
                orderId,
                payment.getId(),
                failureReason
        ));
    }

    /**
     * Scheduled reconciliation of stale AWAITING_PAYMENT orders.
     * Runs every 5 minutes. Checks with gateway if payment was actually captured
     * but webhook was missed, or marks as failed if past threshold.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void reconcileStalledPayments() 
    {
        long thresholdMinutes = paymentGatewayProperties.getStalePaymentThresholdMinutes();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(thresholdMinutes);

        List<Payment> stalePayments = paymentRepository
                .findByPaymentStatusAndCreatedAtBefore(PaymentStatus.AWAITING_PAYMENT, cutoff);

        if (stalePayments.isEmpty()) {
            return;
        }

        log.info("Found {} stale AWAITING_PAYMENT records older than {} minutes",
                stalePayments.size(), thresholdMinutes);

        for (Payment payment : stalePayments) {
            try {
                if (payment.getGatewayOrderId() == null) {
                    // Gateway was never called — mark as failed
                    markPaymentFailed(payment.getOrder().getId(), "Payment initiation timed out");
                    continue;
                }

                // Ask gateway for the actual status
                PaymentVerificationResult result = paymentGateway
                        .verifyPaymentByGatewayOrderId(payment.getGatewayOrderId());

                if (!result.isVerified()) {
                    log.warn("Could not verify stale payment {} — will retry next cycle", payment.getId());
                    continue;
                }

                if (result.getStatus() == PaymentStatus.SUCCESS) {
                    markPaymentSuccess(
                            payment.getOrder().getId(),
                            result.getGatewayPaymentId(),
                            result.getUpiTransactionId()
                    );
                } else {
                    markPaymentFailed(payment.getOrder().getId(), 
                            "Payment not completed within " + thresholdMinutes + " minutes");
                }
            } catch (Exception e) {
                log.error("Error reconciling payment {}: {}", payment.getId(), e.getMessage(), e);
            }
        }
    }
}
