package com.spring.ecommerce.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Razorpay webhook payload structure.
 * @see <a href="https://razorpay.com/docs/webhooks/payloads/payments/">Razorpay Webhooks</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RazorpayWebhookPayload 
{
    private String event;                         // "payment.authorized", "payment.captured", "payment.failed"
    
    @JsonProperty("account_id")
    private String accountId;

    private Payload payload;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload 
    {
        private PaymentWrapper payment;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentWrapper 
    {
        private PaymentEntity entity;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentEntity 
    {
        private String id;                        // "pay_xxx" — Razorpay payment ID

        @JsonProperty("order_id")
        private String orderId;                   // "order_xxx" — Razorpay order ID

        private Long amount;                      // Amount in paise (₹500 = 50000)
        private String currency;                  // "INR"
        private String status;                    // "authorized", "captured", "failed"
        private String method;                    // "upi", "card", "netbanking", "wallet"
        private String vpa;                       // UPI VPA (e.g., "user@googlepay")
        private String email;
        private String contact;

        @JsonProperty("error_code")
        private String errorCode;

        @JsonProperty("error_description")
        private String errorDescription;

        @JsonProperty("error_reason")
        private String errorReason;
        
        @JsonProperty("acquirer_data")
        private AcquirerData acquirerData;
        
        private Notes notes;                      // Custom metadata set during order creation
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AcquirerData 
    {
        @JsonProperty("upi_transaction_id")
        private String upiTransactionId;          // UPI transaction reference
        private String rrn;                       // Bank reference number
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Notes 
    {
        @JsonProperty("order_id")
        private String orderId;                   // Our internal order ID

        @JsonProperty("payment_id")
        private String paymentId;                 // Our internal payment ID
    }
}
