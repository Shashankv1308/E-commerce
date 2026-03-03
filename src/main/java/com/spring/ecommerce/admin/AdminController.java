package com.spring.ecommerce.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.spring.ecommerce.security.CustomUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController 
{
    private final AdminService adminService;

    public AdminController(AdminService adminService) 
    {
        this.adminService = adminService;
    }

    // ── Orders ──────────────────────────────────────────────────

    @GetMapping("/orders")
    public Page<AdminOrderResponse> getAllOrders(
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            Pageable pageable
    ) {
        return adminService.getAllOrders(orderStatus, paymentStatus, pageable);
    }

    @GetMapping("/orders/{orderId}")
    public AdminOrderResponse getOrder(@PathVariable Long orderId) 
    {
        return adminService.getOrderById(orderId);
    }

    @PatchMapping("/orders/{orderId}/status")
    public AdminOrderResponse updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody @Valid UpdateOrderStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return adminService.updateOrderStatus(orderId, request, userDetails.getUsername());
    }

    // ── Payments ────────────────────────────────────────────────

    @GetMapping("/payments")
    public Page<AdminPaymentResponse> getAllPayments(
            @RequestParam(required = false) PaymentStatus paymentStatus,
            Pageable pageable
    ) {
        return adminService.getAllPayments(paymentStatus, pageable);
    }

    @GetMapping("/payments/failures")
    public Page<AdminPaymentResponse> getFailedPayments(Pageable pageable) 
    {
        return adminService.getFailedPayments(pageable);
    }

    @PatchMapping("/payments/{paymentId}/status")
    public AdminPaymentResponse updatePaymentStatus(
            @PathVariable Long paymentId,
            @RequestBody @Valid UpdatePaymentStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return adminService.updatePaymentStatus(paymentId, request, userDetails.getUsername());
    }

    // ── Inventory ───────────────────────────────────────────────

    @GetMapping("/inventory")
    public Page<InventoryResponse> getAllInventory(Pageable pageable) 
    {
        return adminService.getAllInventory(pageable);
    }

    @PatchMapping("/inventory/{productId}")
    public InventoryResponse adjustInventory(
            @PathVariable Long productId,
            @RequestBody @Valid AdjustInventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return adminService.adjustInventory(productId, request, userDetails.getUsername());
    }

    // ── Audit Logs ──────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public Page<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) String adminEmail,
            Pageable pageable
    ) {
        return adminService.getAuditLogs(actionType, adminEmail, pageable);
    }

    // ── Admin Users ─────────────────────────────────────────────

    @PostMapping("/users")
    public SignupResponse createAdminUser(
            @RequestBody @Valid CreateAdminRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return adminService.createAdminUser(request, userDetails.getUsername());
    }
}
