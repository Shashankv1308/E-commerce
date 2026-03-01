package com.spring.ecommerce.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import com.spring.ecommerce.payment.event.PaymentCompletedEvent;
import com.spring.ecommerce.payment.event.PaymentFailedEvent;
import com.spring.ecommerce.payment.gateway.PaymentGateway;
import com.spring.ecommerce.payment.dto.RazorpayWebhookPayload;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.User;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest 
{
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentGatewayProperties paymentGatewayProperties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Order order;
    private Payment payment;
    private User user;

    @BeforeEach
    void setUp() 
    {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPhoneNumber("9876543210");

        order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(500.0);

        payment = new Payment();
        payment.setId(10L);
        payment.setOrder(order);
        payment.setAmount(500.0);
        payment.setPaymentMethod(PaymentMethod.ONLINE);
        payment.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT);
        payment.setCreatedAt(LocalDateTime.now());
    }

    // ── initiatePayment ──────────────────────────────────────────

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePaymentTests 
    {
        @Test
        @DisplayName("should initiate payment successfully via gateway")
        void initiatePayment_success() 
        {
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

            PaymentInitiationResponse gatewayResponse = PaymentInitiationResponse.builder()
                    .gatewayOrderId("gw_order_123")
                    .paymentLink("https://pay.example.com/123")
                    .success(true)
                    .build();
            when(paymentGateway.initiatePayment(any(PaymentInitiationRequest.class)))
                    .thenReturn(gatewayResponse);

            PaymentInitiationResponse result = paymentService.initiatePayment(100L);

            assertTrue(result.isSuccess());
            assertEquals("gw_order_123", result.getGatewayOrderId());

            verify(paymentRepository).save(payment);
            assertEquals("gw_order_123", payment.getGatewayOrderId());
            assertEquals("https://pay.example.com/123", payment.getPaymentLink());
        }

        @Test
        @DisplayName("should skip initiation if payment already processed")
        void initiatePayment_alreadyProcessed() 
        {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setGatewayOrderId("existing_order");
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

            PaymentInitiationResponse result = paymentService.initiatePayment(100L);

            assertTrue(result.isSuccess());
            assertEquals("existing_order", result.getGatewayOrderId());
            verify(paymentGateway, never()).initiatePayment(any());
        }

        @Test
        @DisplayName("should use pessimistic lock via findByOrderIdForUpdate")
        void initiatePayment_usesPessimisticLock() 
        {
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
            when(paymentGateway.initiatePayment(any()))
                    .thenReturn(PaymentInitiationResponse.builder().success(false).build());

            paymentService.initiatePayment(100L);

            verify(paymentRepository).findByOrderIdForUpdate(100L);
            verify(paymentRepository, never()).findByOrderId(anyLong());
        }

        @Test
        @DisplayName("should throw when payment not found")
        void initiatePayment_paymentNotFound() 
        {
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, 
                    () -> paymentService.initiatePayment(100L));
        }

        @Test
        @DisplayName("should handle gateway failure response")
        void initiatePayment_gatewayFailure() 
        {
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
            when(paymentGateway.initiatePayment(any()))
                    .thenReturn(PaymentInitiationResponse.builder()
                            .success(false)
                            .errorMessage("Gateway timeout")
                            .build());

            PaymentInitiationResponse result = paymentService.initiatePayment(100L);

            assertFalse(result.isSuccess());
            verify(paymentRepository, never()).save(any());
        }
    }

    // ── markPaymentSuccess ───────────────────────────────────────

    @Nested
    @DisplayName("markPaymentSuccess")
    class MarkPaymentSuccessTests 
    {
        @Test
        @DisplayName("should update payment and order status to SUCCESS")
        void markSuccess_updatesStatus() 
        {
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentSuccess(100L, "pay_abc", "UPI12345");

            assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
            assertEquals("pay_abc", payment.getGatewayPaymentId());
            assertEquals("UPI12345", payment.getUpiTransactionId());
            assertEquals(PaymentStatus.SUCCESS, order.getPaymentStatus());

            verify(paymentRepository).save(payment);
            verify(orderRepository).save(order);
        }

        @Test
        @DisplayName("should publish PaymentCompletedEvent on success")
        void markSuccess_publishesEvent() 
        {
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentSuccess(100L, "pay_abc", "UPI12345");

            ArgumentCaptor<PaymentCompletedEvent> captor = 
                    ArgumentCaptor.forClass(PaymentCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PaymentCompletedEvent event = captor.getValue();
            assertEquals(100L, event.getOrderId());
            assertEquals(10L, event.getPaymentId());
            assertEquals("pay_abc", event.getGatewayPaymentId());
        }

        @Test
        @DisplayName("should be idempotent - skip if already SUCCESS")
        void markSuccess_idempotent() 
        {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentSuccess(100L, "pay_abc", "UPI12345");

            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("should throw when order not found")
        void markSuccess_orderNotFound() 
        {
            when(orderRepository.findById(100L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.markPaymentSuccess(100L, "pay_abc", "UPI12345"));
        }

        @Test
        @DisplayName("should throw when payment not found")
        void markSuccess_paymentNotFound() 
        {
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.markPaymentSuccess(100L, "pay_abc", "UPI12345"));
        }
    }

    // ── markPaymentFailed ────────────────────────────────────────

    @Nested
    @DisplayName("markPaymentFailed")
    class MarkPaymentFailedTests 
    {
        @Test
        @DisplayName("should cancel order and restore inventory (saga compensation)")
        void markFailed_sagaCompensation() 
        {
            Product product = new Product();
            product.setId(50L);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(3);

            order.setItems(List.of(item));

            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
            when(productRepository.increaseStock(50L, 3)).thenReturn(1);

            paymentService.markPaymentFailed(100L, "Card declined");

            // Verify inventory restoration
            verify(productRepository).increaseStock(50L, 3);

            // Verify status updates
            assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
            assertEquals("Card declined", payment.getFailureReason());
            assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
            assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        }

        @Test
        @DisplayName("should publish PaymentFailedEvent")
        void markFailed_publishesEvent() 
        {
            order.setItems(List.of());
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentFailed(100L, "Timeout");

            ArgumentCaptor<PaymentFailedEvent> captor = 
                    ArgumentCaptor.forClass(PaymentFailedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            assertEquals("Timeout", captor.getValue().getFailureReason());
        }

        @Test
        @DisplayName("should be idempotent - skip if already FAILED or CANCELLED")
        void markFailed_idempotent() 
        {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setItems(List.of());

            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentFailed(100L, "Duplicate");

            verify(productRepository, never()).increaseStock(anyLong(), anyInt());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip compensation if only order is CANCELLED (not payment)")
        void markFailed_orderCancelledOnly() 
        {
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setItems(List.of());

            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.markPaymentFailed(100L, "Already cancelled");

            verify(productRepository, never()).increaseStock(anyLong(), anyInt());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should restore multiple items during compensation")
        void markFailed_multipleItems() 
        {
            Product product1 = new Product();
            product1.setId(50L);
            Product product2 = new Product();
            product2.setId(60L);

            OrderItem item1 = new OrderItem();
            item1.setProduct(product1);
            item1.setQuantity(2);

            OrderItem item2 = new OrderItem();
            item2.setProduct(product2);
            item2.setQuantity(5);

            order.setItems(List.of(item1, item2));

            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
            when(productRepository.increaseStock(50L, 2)).thenReturn(1);
            when(productRepository.increaseStock(60L, 5)).thenReturn(1);

            paymentService.markPaymentFailed(100L, "Gateway error");

            verify(productRepository).increaseStock(50L, 2);
            verify(productRepository).increaseStock(60L, 5);
        }

        @Test
        @DisplayName("should throw when order not found")
        void markFailed_orderNotFound() 
        {
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.markPaymentFailed(100L, "Some reason"));
        }

        @Test
        @DisplayName("should throw when payment not found")
        void markFailed_paymentNotFound() 
        {
            order.setItems(List.of());
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.markPaymentFailed(100L, "Some reason"));
        }
    }

    // ── handleWebhookCallback ────────────────────────────────────

    @Nested
    @DisplayName("handleWebhookCallback")
    class WebhookCallbackTests 
    {
        private RazorpayWebhookPayload buildPayload(String event, String gatewayOrderId, 
                String gatewayPaymentId, String upiTxnId, String errorDescription) 
        {
            RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
            payload.setEvent(event);

            RazorpayWebhookPayload.PaymentEntity entity = new RazorpayWebhookPayload.PaymentEntity();
            entity.setOrderId(gatewayOrderId);
            entity.setId(gatewayPaymentId);
            entity.setErrorDescription(errorDescription);

            if (upiTxnId != null) {
                RazorpayWebhookPayload.AcquirerData acq = new RazorpayWebhookPayload.AcquirerData();
                acq.setUpiTransactionId(upiTxnId);
                entity.setAcquirerData(acq);
            }

            RazorpayWebhookPayload.PaymentWrapper wrapper = new RazorpayWebhookPayload.PaymentWrapper();
            wrapper.setEntity(entity);

            RazorpayWebhookPayload.Payload payloadInner = new RazorpayWebhookPayload.Payload();
            payloadInner.setPayment(wrapper);

            payload.setPayload(payloadInner);
            return payload;
        }

        @Test
        @DisplayName("should throw BusinessException on invalid signature")
        void webhook_invalidSignature() 
        {
            when(paymentGateway.verifyWebhookSignature(anyString(), anyString()))
                    .thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> paymentService.handleWebhookCallback(
                            new RazorpayWebhookPayload(), "bad_sig", "{}"));
        }

        @Test
        @DisplayName("should mark success on payment.captured event")
        void webhook_paymentCaptured_markSuccess() 
        {
            RazorpayWebhookPayload payload = buildPayload(
                    "payment.captured", "gw_order_100", "pay_200", "UPI_TXN_1", null);

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
            when(paymentRepository.findByGatewayOrderId("gw_order_100")).thenReturn(Optional.of(payment));
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payload, "valid_sig", "{}");

            assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
            assertEquals("pay_200", payment.getGatewayPaymentId());
            assertEquals("UPI_TXN_1", payment.getUpiTransactionId());
        }

        @Test
        @DisplayName("should mark success on payment.authorized event")
        void webhook_paymentAuthorized_markSuccess() 
        {
            RazorpayWebhookPayload payload = buildPayload(
                    "payment.authorized", "gw_order_100", "pay_201", null, null);

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
            when(paymentRepository.findByGatewayOrderId("gw_order_100")).thenReturn(Optional.of(payment));
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payload, "valid_sig", "{}");

            assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
            assertEquals("pay_201", payment.getGatewayPaymentId());
            assertNull(payment.getUpiTransactionId()); // no acquirerData
        }

        @Test
        @DisplayName("should mark failure on payment.failed event")
        void webhook_paymentFailed_markFailed() 
        {
            order.setItems(List.of());
            RazorpayWebhookPayload payload = buildPayload(
                    "payment.failed", "gw_order_100", "pay_300", null, "Insufficient funds");

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
            when(paymentRepository.findByGatewayOrderId("gw_order_100")).thenReturn(Optional.of(payment));
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payload, "valid_sig", "{}");

            assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
            assertEquals("Insufficient funds", payment.getFailureReason());
            assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        }

        @Test
        @DisplayName("should use default reason when errorDescription is null on failure")
        void webhook_paymentFailed_nullErrorDescription() 
        {
            order.setItems(List.of());
            RazorpayWebhookPayload payload = buildPayload(
                    "payment.failed", "gw_order_100", "pay_301", null, null);

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
            when(paymentRepository.findByGatewayOrderId("gw_order_100")).thenReturn(Optional.of(payment));
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.handleWebhookCallback(payload, "valid_sig", "{}");

            assertEquals("Payment failed", payment.getFailureReason());
        }

        @Test
        @DisplayName("should silently ignore unknown webhook events")
        void webhook_unknownEvent_ignored() 
        {
            RazorpayWebhookPayload payload = new RazorpayWebhookPayload();
            payload.setEvent("refund.processed");

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

            // Should not throw
            paymentService.handleWebhookCallback(payload, "valid_sig", "{}");

            verify(paymentRepository, never()).findByGatewayOrderId(anyString());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when payment not found for captured event")
        void webhook_paymentNotFound_forCaptured() 
        {
            RazorpayWebhookPayload payload = buildPayload(
                    "payment.captured", "gw_nonexistent", "pay_404", null, null);

            when(paymentGateway.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
            when(paymentRepository.findByGatewayOrderId("gw_nonexistent")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> paymentService.handleWebhookCallback(payload, "valid_sig", "{}"));
        }
    }

    // ── reconcileStalledPayments ─────────────────────────────────

    @Nested
    @DisplayName("reconcileStalledPayments")
    class ReconciliationTests 
    {
        @Test
        @DisplayName("should skip when no stale payments exist")
        void reconcile_noStale() 
        {
            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            paymentService.reconcileStalledPayments();

            verify(paymentGateway, never()).verifyPaymentByGatewayOrderId(anyString());
        }

        @Test
        @DisplayName("should mark as failed when gatewayOrderId is null (initiation timed out)")
        void reconcile_noGatewayOrderId() 
        {
            payment.setGatewayOrderId(null);
            order.setItems(List.of());

            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of(payment));
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.reconcileStalledPayments();

            assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
            assertEquals("Payment initiation timed out", payment.getFailureReason());
        }

        @Test
        @DisplayName("should mark SUCCESS if gateway reports payment captured")
        void reconcile_gatewayCaptured() 
        {
            payment.setGatewayOrderId("gw_order_456");

            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of(payment));
            when(paymentGateway.verifyPaymentByGatewayOrderId("gw_order_456"))
                    .thenReturn(PaymentVerificationResult.builder()
                            .status(PaymentStatus.SUCCESS)
                            .gatewayPaymentId("pay_999")
                            .upiTransactionId("UPI99999")
                            .verified(true)
                            .build());
            when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.reconcileStalledPayments();

            assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
            assertEquals("pay_999", payment.getGatewayPaymentId());
        }

        @Test
        @DisplayName("should skip unverifiable payments (retry next cycle)")
        void reconcile_unverifiable() 
        {
            payment.setGatewayOrderId("gw_order_789");

            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of(payment));
            when(paymentGateway.verifyPaymentByGatewayOrderId("gw_order_789"))
                    .thenReturn(PaymentVerificationResult.builder()
                            .verified(false)
                            .failureReason("Gateway unreachable")
                            .build());

            paymentService.reconcileStalledPayments();

            // Payment status should remain unchanged
            assertEquals(PaymentStatus.AWAITING_PAYMENT, payment.getPaymentStatus());
        }

        @Test
        @DisplayName("should mark FAILED when gateway reports non-SUCCESS status")
        void reconcile_gatewayReportsNonSuccess() 
        {
            payment.setGatewayOrderId("gw_order_expired");
            order.setItems(List.of());

            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of(payment));
            when(paymentGateway.verifyPaymentByGatewayOrderId("gw_order_expired"))
                    .thenReturn(PaymentVerificationResult.builder()
                            .status(PaymentStatus.FAILED)
                            .verified(true)
                            .build());
            when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

            paymentService.reconcileStalledPayments();

            assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
            assertTrue(payment.getFailureReason().contains("not completed within"));
        }

        @Test
        @DisplayName("should continue processing remaining payments when one throws exception")
        void reconcile_exceptionDoesNotStopLoop() 
        {
            Payment payment2 = new Payment();
            payment2.setId(20L);
            payment2.setGatewayOrderId(null);

            Order order2 = new Order();
            order2.setId(200L);
            order2.setItems(List.of());
            payment2.setOrder(order2);
            payment2.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT);

            // First payment will throw, second should still be processed
            payment.setGatewayOrderId("gw_throw");

            when(paymentGatewayProperties.getStalePaymentThresholdMinutes()).thenReturn(20L);
            when(paymentRepository.findByPaymentStatusAndCreatedAtBefore(
                    eq(PaymentStatus.AWAITING_PAYMENT), any(LocalDateTime.class)))
                    .thenReturn(List.of(payment, payment2));

            when(paymentGateway.verifyPaymentByGatewayOrderId("gw_throw"))
                    .thenThrow(new RuntimeException("Gateway API error"));

            when(orderRepository.findWithItemsById(200L)).thenReturn(Optional.of(order2));
            when(paymentRepository.findByOrderId(200L)).thenReturn(Optional.of(payment2));

            paymentService.reconcileStalledPayments();

            // First payment unchanged (exception caught)
            assertEquals(PaymentStatus.AWAITING_PAYMENT, payment.getPaymentStatus());
            // Second payment still processed
            assertEquals(PaymentStatus.FAILED, payment2.getPaymentStatus());
        }
    }

    // ── initiatePayment DTO assertion ─────────────────────────────

    @Nested
    @DisplayName("initiatePayment request building")
    class InitiatePaymentRequestTests 
    {
        @Test
        @DisplayName("should pass correct fields to gateway via PaymentInitiationRequest")
        void initiatePayment_requestFieldsCorrect() 
        {
            when(paymentRepository.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
            when(paymentGateway.initiatePayment(any(PaymentInitiationRequest.class)))
                    .thenReturn(PaymentInitiationResponse.builder().success(false).build());

            paymentService.initiatePayment(100L);

            ArgumentCaptor<PaymentInitiationRequest> captor = 
                    ArgumentCaptor.forClass(PaymentInitiationRequest.class);
            verify(paymentGateway).initiatePayment(captor.capture());

            PaymentInitiationRequest req = captor.getValue();
            assertEquals(100L, req.getOrderId());
            assertEquals(10L, req.getPaymentId());
            assertEquals(500.0, req.getAmount());
            assertEquals("INR", req.getCurrency());
            assertEquals(PaymentMethod.ONLINE, req.getPaymentMethod());
            assertEquals("test@example.com", req.getCustomerEmail());
            assertEquals("9876543210", req.getCustomerPhone());
            assertEquals("Order #100", req.getDescription());
        }
    }
}
