package com.spring.ecommerce.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.payment.dto.RazorpayWebhookPayload;

/**
 * Unit tests for PaymentWebhookController.
 * Tests error handling strategy: parse errors -> 200, business errors -> 200, server -> 500.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookControllerTest 
{
    @Mock 
    private PaymentService paymentService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentWebhookController controller;

    private static final String VALID_JSON = "{\"event\":\"payment.captured\"}";

    @Test
    @DisplayName("should return 200 OK when webhook processed successfully")
    void webhook_success() throws Exception
    {
        RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
        payload.setEvent("payment.captured");

        when(objectMapper.readValue(eq(VALID_JSON), eq(RazorpayWebhookPayload.class)))
                .thenReturn(payload);
        doNothing().when(paymentService)
                .handleWebhookCallback(any(RazorpayWebhookPayload.class), anyString(), anyString());

        ResponseEntity<String> response = controller.handleRazorpayWebhook(VALID_JSON, "valid_sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook processed", response.getBody());
        verify(paymentService).handleWebhookCallback(eq(payload), eq("valid_sig"), eq(VALID_JSON));
    }

    @Test
    @DisplayName("should return 200 for unparseable payload (no retry)")
    void webhook_parseError_returns200() throws Exception
    {
        String badJson = "{invalid json!!";
        when(objectMapper.readValue(eq(badJson), eq(RazorpayWebhookPayload.class)))
                .thenThrow(new JsonProcessingException("Unexpected character") {});

        ResponseEntity<String> response = controller.handleRazorpayWebhook(badJson, "some_sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("unparseable"));
        verify(paymentService, never()).handleWebhookCallback(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("should return 200 for BusinessException to prevent retry storm")
    void webhook_businessError_returns200() throws Exception
    {
        RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
        when(objectMapper.readValue(eq(VALID_JSON), eq(RazorpayWebhookPayload.class)))
                .thenReturn(payload);
        doThrow(new BusinessException("Invalid webhook signature"))
                .when(paymentService)
                .handleWebhookCallback(any(RazorpayWebhookPayload.class), anyString(), anyString());

        ResponseEntity<String> response = controller.handleRazorpayWebhook(VALID_JSON, "bad_sig");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Webhook rejected"));
    }

    @Test
    @DisplayName("should return 500 for unexpected errors (Razorpay retries)")
    void webhook_serverError_returns500() throws Exception
    {
        RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
        when(objectMapper.readValue(eq(VALID_JSON), eq(RazorpayWebhookPayload.class)))
                .thenReturn(payload);
        doThrow(new RuntimeException("Database connection lost"))
                .when(paymentService)
                .handleWebhookCallback(any(RazorpayWebhookPayload.class), anyString(), anyString());

        ResponseEntity<String> response = controller.handleRazorpayWebhook(VALID_JSON, "valid_sig");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal error", response.getBody());
    }

    @Test
    @DisplayName("should handle missing X-Razorpay-Signature header")
    void webhook_missingSignature() throws Exception
    {
        RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
        when(objectMapper.readValue(eq(VALID_JSON), eq(RazorpayWebhookPayload.class)))
                .thenReturn(payload);
        doNothing().when(paymentService)
                .handleWebhookCallback(any(RazorpayWebhookPayload.class), isNull(), anyString());

        ResponseEntity<String> response = controller.handleRazorpayWebhook(VALID_JSON, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).handleWebhookCallback(any(), isNull(), eq(VALID_JSON));
    }

    @Test
    @DisplayName("should return 200 from health endpoint")
    void webhookHealth_returns200() 
    {
        ResponseEntity<String> response = controller.webhookHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Webhook endpoint active", response.getBody());
    }
}
