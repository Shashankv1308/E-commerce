package com.spring.ecommerce.payment.gateway;

import java.util.Random;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.payment.PaymentGatewayProperties;
import com.spring.ecommerce.payment.dto.PaymentInitiationRequest;
import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.dto.PaymentVerificationResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock payment gateway for testing/development.
 * Simulates payment flows without actual gateway calls.
 * 
 * Activated when: payment.gateway.provider=mock
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway 
{
    private final PaymentGatewayProperties properties;
    private final Random random = new Random();

    @Override
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) 
    {
        log.info("MOCK: Initiating payment for order {} amount {}", 
                request.getOrderId(), request.getAmount());

        String gatewayOrderId = "mock_order_" + UUID.randomUUID().toString().substring(0, 8);
        String paymentLink = "https://mock-pay.example.com/pay/" + gatewayOrderId;
        String upiDeepLink = "upi://pay?pa=merchant@upi&pn=Ecommerce&am=" + request.getAmount() 
                + "&tr=" + gatewayOrderId + "&cu=INR";

        long expiryMs = properties.getPaymentLinkExpiryMinutes() * 60 * 1000;

        return PaymentInitiationResponse.builder()
                .gatewayOrderId(gatewayOrderId)
                .paymentLink(paymentLink)
                .upiDeepLink(upiDeepLink)
                .qrCodeUrl("https://mock-pay.example.com/qr/" + gatewayOrderId)
                .expiresAt(System.currentTimeMillis() + expiryMs)
                .success(true)
                .build();
    }

    @Override
    public PaymentVerificationResult verifyPaymentByGatewayOrderId(String gatewayOrderId) 
    {
        log.info("MOCK: Verifying payment by gateway order ID {}", gatewayOrderId);

        // Simulate 80% success rate
        boolean success = random.nextInt(100) < 80;

        return PaymentVerificationResult.builder()
                .gatewayOrderId(gatewayOrderId)
                .gatewayPaymentId(success ? "mock_pay_" + UUID.randomUUID().toString().substring(0, 8) : null)
                .status(success ? PaymentStatus.SUCCESS : PaymentStatus.AWAITING_PAYMENT)
                .amount(0.0)
                .upiTransactionId(success ? "UPI" + System.currentTimeMillis() : null)
                .failureReason(success ? null : "No completed payment found")
                .verified(true)
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) 
    {
        // Mock always returns true for testing
        log.info("MOCK: Verifying webhook signature (always true for mock)");
        return true;
    }
}
