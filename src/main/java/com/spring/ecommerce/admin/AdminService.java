package com.spring.ecommerce.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.spring.ecommerce.admin.dto.AdminOrderResponse;
import com.spring.ecommerce.admin.dto.AdminPaymentResponse;
import com.spring.ecommerce.admin.dto.AdjustInventoryRequest;
import com.spring.ecommerce.admin.dto.AuditLogResponse;
import com.spring.ecommerce.admin.dto.CreateAdminRequest;
import com.spring.ecommerce.admin.dto.InventoryResponse;
import com.spring.ecommerce.admin.dto.UpdateOrderStatusRequest;
import com.spring.ecommerce.admin.dto.UpdatePaymentStatusRequest;
import com.spring.ecommerce.auth.dto.SignupResponse;
import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentStatus;

public interface AdminService 
{
    // ── Orders ──────────────────────────────────────────────────

    Page<AdminOrderResponse> getAllOrders(
            OrderStatus orderStatus, PaymentStatus paymentStatus, Pageable pageable);

    AdminOrderResponse getOrderById(Long orderId);

    AdminOrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request, String adminEmail);

    // ── Payments ────────────────────────────────────────────────

    Page<AdminPaymentResponse> getAllPayments(PaymentStatus paymentStatus, Pageable pageable);

    Page<AdminPaymentResponse> getFailedPayments(Pageable pageable);

    AdminPaymentResponse updatePaymentStatus(Long paymentId, UpdatePaymentStatusRequest request, String adminEmail);

    // ── Inventory ───────────────────────────────────────────────

    Page<InventoryResponse> getAllInventory(Pageable pageable);

    InventoryResponse adjustInventory(Long productId, AdjustInventoryRequest request, String adminEmail);

    // ── Audit Logs ──────────────────────────────────────────────

    Page<AuditLogResponse> getAuditLogs(
            AdminActionType actionType, String adminEmail, Pageable pageable);

    // ── Admin Users ─────────────────────────────────────────────

    SignupResponse createAdminUser(CreateAdminRequest request, String adminEmail);
}
