package com.spring.ecommerce.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.payment.PaymentGatewayProperties;
import com.spring.ecommerce.payment.dto.PaymentInitiationRequest;
import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.dto.PaymentVerificationResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Razorpay payment gateway implementation.
 * Supports UPI (Google Pay, PhonePe, Paytm), Cards, NetBanking.
 * 
 * Activated when: payment.gateway.provider=razorpay
 * 
 * @see <a href="https://razorpay.com/docs/api/">Razorpay API Docs</a>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "razorpay")
public class RazorpayGateway implements PaymentGateway 
{
    private final RazorpayClient razorpayClient;
    private final PaymentGatewayProperties properties;

    public RazorpayGateway(PaymentGatewayProperties properties) throws RazorpayException 
    {
        this.properties = properties;
        this.razorpayClient = new RazorpayClient(
                properties.getRazorpay().getKeyId(),
                properties.getRazorpay().getKeySecret()
        );
        log.info("Razorpay gateway initialized");
    }

    @Override
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) 
    {
        try {
            // Razorpay amounts are in paise (1 INR = 100 paise)
            long amountInPaise = Math.round(request.getAmount() * 100);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", request.getCurrency() != null ? request.getCurrency() : "INR");
            orderRequest.put("receipt", "order_" + request.getOrderId());
            orderRequest.put("payment_capture", 1); // Auto-capture payment

            // Store our internal IDs in notes for webhook reference
            JSONObject notes = new JSONObject();
            notes.put("order_id", request.getOrderId().toString());
            notes.put("payment_id", request.getPaymentId().toString());
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);
            String gatewayOrderId = order.get("id");
            String shortUrl = order.has("short_url") ? order.get("short_url") : null;

            log.info("Razorpay order created: {} for internal order {}", 
                    gatewayOrderId, request.getOrderId());

            // Build UPI deep link (for UPI payments)
            String upiDeepLink = buildUpiDeepLink(request, gatewayOrderId);

            return PaymentInitiationResponse.builder()
                    .gatewayOrderId(gatewayOrderId)
                    .paymentLink(shortUrl)
                    .upiDeepLink(upiDeepLink)
                    .expiresAt(System.currentTimeMillis() + properties.getPaymentLinkExpiryMinutes() * 60 * 1000)
                    .success(true)
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage(), e);
            return PaymentInitiationResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) 
    {
        try {
            String expectedSignature = calculateHmacSha256(
                    payload, 
                    properties.getRazorpay().getWebhookSecret()
            );
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildUpiDeepLink(PaymentInitiationRequest request, String gatewayOrderId) 
    {
        // UPI deep link format for payment apps (Google Pay, PhonePe, etc.)
        return String.format(
                "upi://pay?pa=%s&pn=%s&am=%.2f&tr=%s&cu=INR&mode=02",
                properties.getRazorpay().getMerchantVpa(),
                "Ecommerce",
                request.getAmount(),
                gatewayOrderId
        );
    }

    @Override
    public PaymentVerificationResult verifyPaymentByGatewayOrderId(String gatewayOrderId) 
    {
        try {
            // Fetch all payment attempts for this Razorpay order
            java.util.List<Payment> payments = razorpayClient.orders.fetchPayments(gatewayOrderId);
            
            for (Payment payment : payments) {
                String status = payment.get("status");
                if ("captured".equals(status) || "authorized".equals(status)) {
                    String upiTxnId = null;
                    if (payment.has("acquirer_data")) {
                        JSONObject acquirerData = payment.get("acquirer_data");
                        if (acquirerData.has("upi_transaction_id")) {
                            upiTxnId = acquirerData.getString("upi_transaction_id");
                        }
                    }
                    return PaymentVerificationResult.builder()
                            .gatewayPaymentId(payment.get("id"))
                            .gatewayOrderId(gatewayOrderId)
                            .status(PaymentStatus.SUCCESS)
                            .amount(((Number) payment.get("amount")).doubleValue() / 100.0)
                            .upiTransactionId(upiTxnId)
                            .verified(true)
                            .build();
                }
            }

            // No successful payment found
            return PaymentVerificationResult.builder()
                    .gatewayOrderId(gatewayOrderId)
                    .status(PaymentStatus.AWAITING_PAYMENT)
                    .verified(true)
                    .failureReason("No completed payment found for order")
                    .build();

        } catch (RazorpayException e) {
            log.error("Payment verification by order ID failed: {}", e.getMessage(), e);
            return PaymentVerificationResult.builder()
                    .gatewayOrderId(gatewayOrderId)
                    .verified(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    private PaymentStatus mapRazorpayStatus(String razorpayStatus) 
    {
        return switch (razorpayStatus) {
            case "captured", "authorized" -> PaymentStatus.SUCCESS;
            case "failed" -> PaymentStatus.FAILED;
            case "refunded" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.AWAITING_PAYMENT;
        };
    }

    private String calculateHmacSha256(String data, String secret) throws Exception 
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
