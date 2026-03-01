package com.spring.ecommerce.payment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.payment.dto.PaymentInitiationResponse;
import com.spring.ecommerce.payment.event.OrderPlacedEvent;
import com.spring.ecommerce.payment.event.PaymentCompletedEvent;
import com.spring.ecommerce.payment.event.PaymentFailedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest 
{
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentEventListener listener;

    private OrderPlacedEvent createOnlineEvent() 
    {
        return new OrderPlacedEvent(
                100L, 10L, 1L, 500.0,
                PaymentMethod.ONLINE,
                "test@example.com", "9876543210"
        );
    }

    @Test
    @DisplayName("should initiate payment for ONLINE order event")
    void handleOrderPlaced_online_success() 
    {
        when(paymentService.initiatePayment(100L))
                .thenReturn(PaymentInitiationResponse.builder()
                        .success(true)
                        .gatewayOrderId("gw_123")
                        .paymentLink("https://pay.example.com/123")
                        .build());

        listener.handleOrderPlaced(createOnlineEvent());

        verify(paymentService).initiatePayment(100L);
        verify(paymentService, never()).markPaymentFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("should call markPaymentFailed when gateway initiation fails")
    void handleOrderPlaced_gatewayFails() 
    {
        when(paymentService.initiatePayment(100L))
                .thenReturn(PaymentInitiationResponse.builder()
                        .success(false)
                        .errorMessage("Gateway timeout")
                        .build());

        listener.handleOrderPlaced(createOnlineEvent());

        verify(paymentService).markPaymentFailed(eq(100L), contains("Gateway initiation failed"));
    }

    @Test
    @DisplayName("should compensate on unexpected exception during initiation")
    void handleOrderPlaced_exceptionCompensation() 
    {
        when(paymentService.initiatePayment(100L))
                .thenThrow(new RuntimeException("DB connection lost"));

        listener.handleOrderPlaced(createOnlineEvent());

        verify(paymentService).markPaymentFailed(eq(100L), contains("Payment initiation error"));
    }

    @Test
    @DisplayName("should handle compensation failure gracefully (no rethrow)")
    void handleOrderPlaced_compensationAlsoFails() 
    {
        when(paymentService.initiatePayment(100L))
                .thenThrow(new RuntimeException("DB connection lost"));
        doThrow(new RuntimeException("Compensation failed"))
                .when(paymentService).markPaymentFailed(anyLong(), anyString());

        // Should not throw even if compensation fails
        listener.handleOrderPlaced(createOnlineEvent());

        verify(paymentService).markPaymentFailed(eq(100L), contains("Payment initiation error"));
    }

    @Test
    @DisplayName("handlePaymentCompleted should not throw")
    void handlePaymentCompleted_logs() 
    {
        PaymentCompletedEvent event = new PaymentCompletedEvent(100L, 10L, "pay_abc", "gw_123");

        // Should complete without errors
        listener.handlePaymentCompleted(event);
    }

    @Test
    @DisplayName("handlePaymentFailed should not throw")
    void handlePaymentFailed_logs() 
    {
        PaymentFailedEvent event = new PaymentFailedEvent(100L, 10L, "Card declined");

        // Should complete without errors
        listener.handlePaymentFailed(event);
    }
}
