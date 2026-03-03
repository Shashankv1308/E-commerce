package com.spring.ecommerce.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.ecommerce.admin.dto.AdminOrderResponse;
import com.spring.ecommerce.admin.dto.AdminPaymentResponse;
import com.spring.ecommerce.admin.dto.AdjustInventoryRequest;
import com.spring.ecommerce.admin.dto.AuditLogResponse;
import com.spring.ecommerce.admin.dto.CreateAdminRequest;
import com.spring.ecommerce.admin.dto.InventoryResponse;
import com.spring.ecommerce.admin.dto.UpdateOrderStatusRequest;
import com.spring.ecommerce.admin.dto.UpdatePaymentStatusRequest;
import com.spring.ecommerce.auth.dto.SignupResponse;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.inventory.InventoryRepository;
import com.spring.ecommerce.order.Order;
import com.spring.ecommerce.order.OrderItem;
import com.spring.ecommerce.order.OrderRepository;
import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.order.dto.OrderItemResponse;
import com.spring.ecommerce.payment.Payment;
import com.spring.ecommerce.payment.PaymentRepository;
import com.spring.ecommerce.payment.PaymentService;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.Role;
import com.spring.ecommerce.user.User;
import com.spring.ecommerce.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService 
{
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogRepository auditLogRepository;

    // ── Orders ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAllOrders(
            OrderStatus orderStatus, PaymentStatus paymentStatus, Pageable pageable) 
    {
        Page<Order> orders;

        if (orderStatus != null && paymentStatus != null) {
            orders = orderRepository.findByOrderStatusAndPaymentStatus(
                    orderStatus, paymentStatus, pageable);
        } else if (orderStatus != null) {
            orders = orderRepository.findByOrderStatus(orderStatus, pageable);
        } else if (paymentStatus != null) {
            orders = orderRepository.findByPaymentStatus(paymentStatus, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(this::mapToAdminOrderResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderById(Long orderId) 
    {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        
        AdminOrderResponse response = mapToAdminOrderResponse(order);

        // Attach gateway order ID if payment exists
        paymentRepository.findByOrderId(orderId)
                .ifPresent(payment -> response.setGatewayOrderId(payment.getGatewayOrderId()));

        return response;
    }

    @Override
    public AdminOrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request, String adminEmail) 
    {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        OrderStatus currentStatus = order.getOrderStatus();
        OrderStatus targetStatus = request.getStatus();

        validateOrderTransition(currentStatus, targetStatus);

        String oldStatus = currentStatus.name();

        if (targetStatus == OrderStatus.CANCELLED) {
            // Saga compensation: restore inventory
            for (OrderItem item : order.getItems()) {
                productRepository.increaseStock(
                        item.getProduct().getId(),
                        item.getQuantity()
                );
                log.info("Admin cancelled: restored {} units of product {} for order {}",
                        item.getQuantity(), item.getProduct().getId(), orderId);
            }
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.REFUNDED);

            recordAuditLog(adminEmail, AdminActionType.ORDER_CANCELLED, "Order", orderId,
                    "status: " + oldStatus + " → CANCELLED (with inventory restoration)");
        } else {
            order.setOrderStatus(targetStatus);

            recordAuditLog(adminEmail, AdminActionType.ORDER_STATUS_UPDATE, "Order", orderId,
                    "status: " + oldStatus + " → " + targetStatus.name());
        }

        orderRepository.save(order);
        log.info("Admin {} updated order {} status: {} → {}", adminEmail, orderId, oldStatus, targetStatus);

        return mapToAdminOrderResponse(order);
    }

    private void validateOrderTransition(OrderStatus current, OrderStatus target) 
    {
        if (current == OrderStatus.DELIVERED) {
            throw new BusinessException("Cannot modify a delivered order");
        }
        if (current == OrderStatus.CANCELLED) {
            throw new BusinessException("Cannot modify a cancelled order");
        }

        // Valid forward transitions
        boolean valid = switch (current) {
            case CONFIRMED -> target == OrderStatus.SHIPPED || target == OrderStatus.CANCELLED;
            case SHIPPED -> target == OrderStatus.DELIVERED || target == OrderStatus.CANCELLED;
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    "Invalid status transition: " + current + " → " + target);
        }
    }

    // ── Payments ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> getAllPayments(PaymentStatus paymentStatus, Pageable pageable) 
    {
        Page<Payment> payments;

        if (paymentStatus != null) {
            payments = paymentRepository.findByPaymentStatus(paymentStatus, pageable);
        } else {
            payments = paymentRepository.findAll(pageable);
        }

        return payments.map(this::mapToAdminPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> getFailedPayments(Pageable pageable) 
    {
        return paymentRepository.findByPaymentStatus(PaymentStatus.FAILED, pageable)
                .map(this::mapToAdminPaymentResponse);
    }

    @Override
    public AdminPaymentResponse updatePaymentStatus(Long paymentId, UpdatePaymentStatusRequest request, String adminEmail) 
    {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        Long orderId = payment.getOrder().getId();
        String oldStatus = payment.getPaymentStatus().name();
        PaymentStatus targetStatus = request.getStatus();

        if (targetStatus == PaymentStatus.SUCCESS) {
            paymentService.markPaymentSuccess(
                    orderId,
                    request.getGatewayPaymentId(),
                    null
            );
        } else if (targetStatus == PaymentStatus.FAILED) {
            String reason = request.getReason() != null 
                    ? request.getReason() 
                    : "Manually marked as failed by admin";
            paymentService.markPaymentFailed(orderId, reason);
        } else {
            throw new BusinessException(
                    "Admin can only set payment status to SUCCESS or FAILED, not: " + targetStatus);
        }

        recordAuditLog(adminEmail, AdminActionType.PAYMENT_STATUS_UPDATE, "Payment", paymentId,
                "status: " + oldStatus + " → " + targetStatus.name() + " (orderId=" + orderId + ")");

        log.info("Admin {} updated payment {} status: {} → {}", adminEmail, paymentId, oldStatus, targetStatus);

        // Re-fetch to return updated state
        Payment updated = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        return mapToAdminPaymentResponse(updated);
    }

    // ── Inventory ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryResponse> getAllInventory(Pageable pageable) 
    {
        return inventoryRepository.findAll(pageable)
                .map(this::mapToInventoryResponse);
    }

    @Override
    public InventoryResponse adjustInventory(Long productId, AdjustInventoryRequest request, String adminEmail) 
    {
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));

        int oldStock = inventory.getAvailableStock();
        int quantity = request.getQuantity();

        if (request.getAdjustmentType() == AdjustInventoryRequest.AdjustmentType.ABSOLUTE) {
            inventoryRepository.setStock(productId, quantity);

            recordAuditLog(adminEmail, AdminActionType.INVENTORY_ADJUSTED, "Inventory", productId,
                    "availableStock: " + oldStock + " → " + quantity + " (ABSOLUTE)");

            log.info("Admin {} set inventory for product {} to {} (was {})", 
                    adminEmail, productId, quantity, oldStock);
        } else {
            // DELTA: positive means add stock
            if (quantity > 0) {
                productRepository.increaseStock(productId, quantity);
            } else if (quantity < 0) {
                throw new BusinessException("DELTA quantity must be non-negative. Use ABSOLUTE to reduce stock.");
            }

            recordAuditLog(adminEmail, AdminActionType.INVENTORY_ADJUSTED, "Inventory", productId,
                    "availableStock: " + oldStock + " → " + (oldStock + quantity) + " (DELTA +" + quantity + ")");

            log.info("Admin {} adjusted inventory for product {} by +{} (was {})", 
                    adminEmail, productId, quantity, oldStock);
        }

        // Re-fetch updated inventory
        Inventory updated = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
        return mapToInventoryResponse(updated);
    }

    // ── Audit Logs ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(
            AdminActionType actionType, String adminEmail, Pageable pageable) 
    {
        Page<AdminAuditLog> logs;

        if (actionType != null) {
            logs = auditLogRepository.findByActionType(actionType, pageable);
        } else if (adminEmail != null && !adminEmail.isBlank()) {
            logs = auditLogRepository.findByAdminEmail(adminEmail, pageable);
        } else {
            logs = auditLogRepository.findAll(pageable);
        }

        return logs.map(this::mapToAuditLogResponse);
    }

    // ── Admin Users ─────────────────────────────────────────────

    @Override
    public SignupResponse createAdminUser(CreateAdminRequest request, String adminEmail) 
    {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered");
        }

        User admin = new User();
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setPhoneNumber(request.getPhoneNumber());
        admin.setRole(Role.ADMIN);

        userRepository.save(admin);

        recordAuditLog(adminEmail, AdminActionType.ADMIN_USER_CREATED, "User", admin.getId(),
                "Created admin user: " + request.getEmail());

        log.info("Admin {} created new admin user: {}", adminEmail, request.getEmail());

        return new SignupResponse("Admin user created successfully");
    }

    // ── Helpers ─────────────────────────────────────────────────

    private void recordAuditLog(String adminEmail, AdminActionType actionType, 
                                 String targetEntity, Long targetId, String details) 
    {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminEmail(adminEmail);
        auditLog.setActionType(actionType);
        auditLog.setTargetEntity(targetEntity);
        auditLog.setTargetId(targetId);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }

    private AdminOrderResponse mapToAdminOrderResponse(Order order) 
    {
        AdminOrderResponse response = new AdminOrderResponse();
        response.setOrderId(order.getId());
        response.setUserId(order.getUser().getId());
        response.setUserEmail(order.getUser().getEmail());
        response.setOrderStatus(order.getOrderStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());

        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                OrderItemResponse itemResponse = new OrderItemResponse();
                itemResponse.setProductId(item.getProduct().getId());
                itemResponse.setProductName(item.getProduct().getName());
                itemResponse.setQuantity(item.getQuantity());
                itemResponse.setPriceAtPurchase(item.getPriceAtPurchase());
                itemResponse.setSubtotal(item.getQuantity() * item.getPriceAtPurchase());
                itemResponses.add(itemResponse);
            }
        }
        response.setItems(itemResponses);

        return response;
    }

    private AdminPaymentResponse mapToAdminPaymentResponse(Payment payment) 
    {
        AdminPaymentResponse response = new AdminPaymentResponse();
        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getOrder().getId());
        response.setUserEmail(payment.getOrder().getUser().getEmail());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setPaymentStatus(payment.getPaymentStatus());
        response.setAmount(payment.getAmount());
        response.setGatewayOrderId(payment.getGatewayOrderId());
        response.setGatewayPaymentId(payment.getGatewayPaymentId());
        response.setUpiTransactionId(payment.getUpiTransactionId());
        response.setFailureReason(payment.getFailureReason());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }

    private InventoryResponse mapToInventoryResponse(Inventory inventory) 
    {
        InventoryResponse response = new InventoryResponse();
        response.setProductId(inventory.getProductId());
        response.setProductName(inventory.getProduct().getName());
        response.setAvailableStock(inventory.getAvailableStock());
        response.setTotalStock(inventory.getTotalStock());
        response.setActive(inventory.getProduct().isActive());
        return response;
    }

    private AuditLogResponse mapToAuditLogResponse(AdminAuditLog auditLog) 
    {
        AuditLogResponse response = new AuditLogResponse();
        response.setId(auditLog.getId());
        response.setAdminEmail(auditLog.getAdminEmail());
        response.setActionType(auditLog.getActionType());
        response.setTargetEntity(auditLog.getTargetEntity());
        response.setTargetId(auditLog.getTargetId());
        response.setDetails(auditLog.getDetails());
        response.setCreatedAt(auditLog.getCreatedAt());
        return response;
    }
}
