package com.spring.ecommerce.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.payment.dto.RazorpayWebhookPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook endpoint for payment gateway callbacks.
 * 
 * Razorpay sends POST requests here when payment status changes.
 * This endpoint must be publicly accessible (configure in gateway dashboard).
 * 
 * Security:
 * - Signature verification ensures request authenticity
 * - Should be excluded from CSRF protection
 * - Rate limiting recommended in production
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController 
{
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Razorpay webhook endpoint.
     * 
     * Configure this URL in Razorpay Dashboard:
     * https://your-domain.com/webhooks/razorpay
     * 
     * Events to enable:
     * - payment.captured
     * - payment.failed
     * 
     * @param rawPayload Raw JSON payload for signature verification
     * @param signature X-Razorpay-Signature header
     * @return 200 OK always (prevents Razorpay retry storms), 500 only for transient errors
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        log.info("Received Razorpay webhook");
        log.debug("Webhook payload: {}", rawPayload);

        try {
            // Parse payload
            RazorpayWebhookPayload payload = objectMapper.readValue(
                    rawPayload, 
                    RazorpayWebhookPayload.class
            );

            // Process the webhook
            paymentService.handleWebhookCallback(payload, signature, rawPayload);

            return ResponseEntity.ok("Webhook processed");

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Parse error — deterministic, return 200 so Razorpay does NOT retry
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.ok("Webhook payload unparseable — ignored");

        } catch (BusinessException e) {
            // Business-level rejection (invalid signature, duplicate, etc.)
            // Return 200 to prevent Razorpay retry storm — reconciliation job is the safety net
            log.warn("Business error processing webhook: {}", e.getMessage());
            return ResponseEntity.ok("Webhook rejected: " + e.getMessage());

        } catch (Exception e) {
            // Unexpected server error — return 500 so Razorpay WILL retry
            // Reconciliation job is the safety net for missed webhooks
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal error");
        }
    }

    /**
     * Health check endpoint for webhook.
     * Useful for gateway dashboard verification.
     */
    @PostMapping("/razorpay/health")
    public ResponseEntity<String> webhookHealth() 
    {
        return ResponseEntity.ok("Webhook endpoint active");
    }
}
