package com.spring.ecommerce.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.spring.ecommerce.admin.dto.AdjustInventoryRequest;
import com.spring.ecommerce.admin.dto.AdjustInventoryRequest.AdjustmentType;
import com.spring.ecommerce.admin.dto.AdminOrderResponse;
import com.spring.ecommerce.admin.dto.AdminPaymentResponse;
import com.spring.ecommerce.admin.dto.AuditLogResponse;
import com.spring.ecommerce.admin.dto.CreateAdminRequest;
import com.spring.ecommerce.admin.dto.InventoryResponse;
import com.spring.ecommerce.admin.dto.UpdateOrderStatusRequest;
import com.spring.ecommerce.admin.dto.UpdatePaymentStatusRequest;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.inventory.InventoryRepository;
import com.spring.ecommerce.order.Order;
import com.spring.ecommerce.order.OrderItem;
import com.spring.ecommerce.order.OrderRepository;
import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.payment.Payment;
import com.spring.ecommerce.payment.PaymentRepository;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.Role;
import com.spring.ecommerce.user.User;
import com.spring.ecommerce.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminServiceIntegrationTest
{
    @Autowired private AdminService adminService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final Pageable PAGE = PageRequest.of(0, 20);

    @BeforeEach
    void cleanDatabase()
    {
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM admin_audit_logs").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM order_items").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM payments").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM cart_items").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM carts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM inventory").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM products").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });
    }

    // ── Orders ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order Management")
    class OrderManagementTests
    {
        @Test
        @DisplayName("should return all orders paged")
        void getAllOrders_shouldReturnAllOrders()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);
            createOrder(user, p, 1, OrderStatus.SHIPPED,   PaymentStatus.SUCCESS);
            createOrder(user, p, 1, OrderStatus.DELIVERED, PaymentStatus.SUCCESS);

            Page<AdminOrderResponse> result = adminService.getAllOrders(null, null, PAGE);

            assertEquals(3, result.getTotalElements());
        }

        @Test
        @DisplayName("should filter orders by order status")
        void getAllOrders_filteredByOrderStatus_shouldReturnFiltered()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);
            createOrder(user, p, 1, OrderStatus.SHIPPED,   PaymentStatus.SUCCESS);

            Page<AdminOrderResponse> result = adminService.getAllOrders(OrderStatus.CONFIRMED, null, PAGE);

            assertEquals(1, result.getTotalElements());
            assertEquals(OrderStatus.CONFIRMED, result.getContent().get(0).getOrderStatus());
        }

        @Test
        @DisplayName("should filter orders by payment status")
        void getAllOrders_filteredByPaymentStatus_shouldReturnFiltered()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);

            Page<AdminOrderResponse> result = adminService.getAllOrders(null, PaymentStatus.AWAITING_PAYMENT, PAGE);

            assertEquals(1, result.getTotalElements());
            assertEquals(PaymentStatus.AWAITING_PAYMENT, result.getContent().get(0).getPaymentStatus());
        }

        @Test
        @DisplayName("should return order by id with correct items, user email and amounts")
        void getOrderById_shouldReturnOrderWithItems()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 150.0, 50);
            Order order = createOrder(user, p, 3, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);

            AdminOrderResponse response = adminService.getOrderById(order.getId());

            assertEquals(order.getId(), response.getOrderId());
            assertEquals("user@test.com", response.getUserEmail());
            assertEquals(OrderStatus.CONFIRMED, response.getOrderStatus());
            assertEquals(1, response.getItems().size());
            assertEquals(3, response.getItems().get(0).getQuantity());
            assertEquals(150.0, response.getItems().get(0).getPriceAtPurchase());
            assertEquals(450.0, response.getItems().get(0).getSubtotal());
        }

        @Test
        @DisplayName("should update CONFIRMED order to SHIPPED and record ORDER_STATUS_UPDATE audit log")
        void updateOrderStatus_confirmedToShipped_shouldUpdateStatusAndAuditLog()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.SHIPPED);

            AdminOrderResponse response = adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL);

            assertEquals(OrderStatus.SHIPPED, response.getOrderStatus());

            Page<AuditLogResponse> logs = adminService.getAuditLogs(
                    AdminActionType.ORDER_STATUS_UPDATE, null, PAGE);
            assertEquals(1, logs.getTotalElements());
            assertEquals(ADMIN_EMAIL, logs.getContent().get(0).getAdminEmail());
            assertTrue(logs.getContent().get(0).getDetails().contains("CONFIRMED"));
            assertTrue(logs.getContent().get(0).getDetails().contains("SHIPPED"));
        }

        @Test
        @DisplayName("should update SHIPPED order to DELIVERED")
        void updateOrderStatus_shippedToDelivered_shouldUpdateStatus()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.SHIPPED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.DELIVERED);

            AdminOrderResponse response = adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL);

            assertEquals(OrderStatus.DELIVERED, response.getOrderStatus());
        }

        @Test
        @DisplayName("should cancel CONFIRMED order, restore inventory, set REFUNDED, and record ORDER_CANCELLED audit log")
        void cancelOrder_shouldRestoreInventoryAndCreateAuditLog()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            // createOrder decreases stock by 3 → availableStock = 47
            Order order = createOrder(user, p, 3, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);

            int stockBefore = inventoryRepository.findById(p.getId()).orElseThrow().getAvailableStock();
            assertEquals(47, stockBefore);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CANCELLED);

            AdminOrderResponse response = adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL);

            assertEquals(OrderStatus.CANCELLED, response.getOrderStatus());
            assertEquals(PaymentStatus.REFUNDED, response.getPaymentStatus());

            // Stock should be restored back to 50
            entityManager.flush();
            entityManager.clear();
            int stockAfter = inventoryRepository.findById(p.getId()).orElseThrow().getAvailableStock();
            assertEquals(50, stockAfter);

            // Audit log action type should be ORDER_CANCELLED, not ORDER_STATUS_UPDATE
            Page<AuditLogResponse> logs = adminService.getAuditLogs(
                    AdminActionType.ORDER_CANCELLED, null, PAGE);
            assertEquals(1, logs.getTotalElements());
            assertTrue(logs.getContent().get(0).getDetails().contains("inventory restoration"));
        }
    }

    // ── Order Status Transition Validation ────────────────────────

    @Nested
    @DisplayName("Order Status Transition Validation")
    class OrderStatusTransitionValidationTests
    {
        @Test
        @DisplayName("should throw BusinessException when modifying a DELIVERED order")
        void updateOrderStatus_deliveredOrder_shouldThrowBusinessException()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.DELIVERED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.SHIPPED);

            assertThrows(BusinessException.class,
                    () -> adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL));
        }

        @Test
        @DisplayName("should throw BusinessException when modifying a CANCELLED order")
        void updateOrderStatus_cancelledOrder_shouldThrowBusinessException()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.CANCELLED, PaymentStatus.REFUNDED);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.SHIPPED);

            assertThrows(BusinessException.class,
                    () -> adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL));
        }

        @Test
        @DisplayName("should throw BusinessException for invalid transition CONFIRMED → DELIVERED (must ship first)")
        void updateOrderStatus_confirmedToDelivered_invalidTransition_shouldThrow()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.DELIVERED);

            assertThrows(BusinessException.class,
                    () -> adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL));
        }

        @Test
        @DisplayName("should throw BusinessException for invalid transition SHIPPED → CONFIRMED (backward)")
        void updateOrderStatus_shippedToConfirmed_invalidTransition_shouldThrow()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.SHIPPED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CONFIRMED);

            assertThrows(BusinessException.class,
                    () -> adminService.updateOrderStatus(order.getId(), req, ADMIN_EMAIL));
        }
    }

    // ── Payments ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Payment Management")
    class PaymentManagementTests
    {
        @Test
        @DisplayName("should return all payments paged")
        void getAllPayments_shouldReturnAllPayments()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            Order o1 = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            Order o2 = createOrder(user, p, 1, OrderStatus.CANCELLED, PaymentStatus.FAILED);

            createPayment(o1, PaymentStatus.SUCCESS);
            createPayment(o2, PaymentStatus.FAILED);

            Page<AdminPaymentResponse> result = adminService.getAllPayments(null, PAGE);

            assertEquals(2, result.getTotalElements());
        }

        @Test
        @DisplayName("should filter payments by payment status")
        void getAllPayments_filterByStatus_shouldReturnMatchingPayments()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            Order o1 = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            Order o2 = createOrder(user, p, 1, OrderStatus.CANCELLED, PaymentStatus.FAILED);

            createPayment(o1, PaymentStatus.SUCCESS);
            createPayment(o2, PaymentStatus.FAILED);

            Page<AdminPaymentResponse> result = adminService.getAllPayments(PaymentStatus.FAILED, PAGE);

            assertEquals(1, result.getTotalElements());
            assertEquals(PaymentStatus.FAILED, result.getContent().get(0).getPaymentStatus());
        }

        @Test
        @DisplayName("should return only FAILED payments via getFailedPayments")
        void getFailedPayments_shouldReturnOnlyFailed()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            Order o1 = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            Order o2 = createOrder(user, p, 1, OrderStatus.CANCELLED, PaymentStatus.FAILED);
            Order o3 = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);

            createPayment(o1, PaymentStatus.SUCCESS);
            createPayment(o2, PaymentStatus.FAILED);
            createPayment(o3, PaymentStatus.AWAITING_PAYMENT);

            Page<AdminPaymentResponse> result = adminService.getFailedPayments(PAGE);

            assertEquals(1, result.getTotalElements());
            assertEquals(PaymentStatus.FAILED, result.getContent().get(0).getPaymentStatus());
        }

        @Test
        @DisplayName("should mark payment as SUCCESS and update order payment status, record audit log")
        void updatePaymentStatus_toSuccess_shouldUpdatePaymentAndOrderStatus()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);
            Payment payment = createPayment(order, PaymentStatus.AWAITING_PAYMENT);

            UpdatePaymentStatusRequest req = new UpdatePaymentStatusRequest();
            req.setStatus(PaymentStatus.SUCCESS);
            req.setGatewayPaymentId("pay_admin_999");

            AdminPaymentResponse response = adminService.updatePaymentStatus(
                    payment.getId(), req, ADMIN_EMAIL);

            assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
            assertEquals("pay_admin_999", response.getGatewayPaymentId());

            // Order's paymentStatus should also be updated
            entityManager.flush();
            entityManager.clear();
            Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(PaymentStatus.SUCCESS, updatedOrder.getPaymentStatus());

            // Audit log
            Page<AuditLogResponse> logs = adminService.getAuditLogs(
                    AdminActionType.PAYMENT_STATUS_UPDATE, null, PAGE);
            assertEquals(1, logs.getTotalElements());
            assertTrue(logs.getContent().get(0).getDetails().contains("SUCCESS"));
        }

        @Test
        @DisplayName("should mark payment as FAILED and execute saga compensation (restore inventory, cancel order)")
        void updatePaymentStatus_toFailed_shouldRestoreInventoryAndCancelOrder()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            // Order for 5 units → availableStock = 45
            Order order = createOrder(user, p, 5, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);
            Payment payment = createPayment(order, PaymentStatus.AWAITING_PAYMENT);

            UpdatePaymentStatusRequest req = new UpdatePaymentStatusRequest();
            req.setStatus(PaymentStatus.FAILED);
            req.setReason("Card declined by bank");

            adminService.updatePaymentStatus(payment.getId(), req, ADMIN_EMAIL);

            entityManager.flush();
            entityManager.clear();

            // Inventory restored: 45 → 50
            int stockAfter = inventoryRepository.findById(p.getId()).orElseThrow().getAvailableStock();
            assertEquals(50, stockAfter);

            // Order cancelled, payment failed
            Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderStatus.CANCELLED, updatedOrder.getOrderStatus());
            assertEquals(PaymentStatus.FAILED, updatedOrder.getPaymentStatus());
        }

        @Test
        @DisplayName("should throw BusinessException when trying to set payment status to REFUNDED (not allowed)")
        void updatePaymentStatus_toRefunded_shouldThrowBusinessException()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);
            Order order = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT);
            Payment payment = createPayment(order, PaymentStatus.AWAITING_PAYMENT);

            UpdatePaymentStatusRequest req = new UpdatePaymentStatusRequest();
            req.setStatus(PaymentStatus.REFUNDED);

            assertThrows(BusinessException.class,
                    () -> adminService.updatePaymentStatus(payment.getId(), req, ADMIN_EMAIL));
        }
    }

    // ── Inventory ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Inventory Management")
    class InventoryManagementTests
    {
        @Test
        @DisplayName("should return all inventory paged with product details")
        void getAllInventory_shouldReturnInventoryWithProductNames()
        {
            createProductWithInventory("Headphones", 250.0, 40);
            createProductWithInventory("Charger",    30.0,  200);

            Page<InventoryResponse> result = adminService.getAllInventory(PAGE);

            assertEquals(2, result.getTotalElements());
            assertTrue(result.getContent().stream()
                    .anyMatch(r -> "Headphones".equals(r.getProductName())));
        }

        @Test
        @DisplayName("ABSOLUTE adjustment should set both availableStock and totalStock to the given quantity")
        void adjustInventory_absolute_shouldSetBothStocks()
        {
            Product p = createProductWithInventory("Product A", 100.0, 20);

            AdjustInventoryRequest req = new AdjustInventoryRequest();
            req.setAdjustmentType(AdjustmentType.ABSOLUTE);
            req.setQuantity(75);

            InventoryResponse response = adminService.adjustInventory(p.getId(), req, ADMIN_EMAIL);

            assertEquals(75, response.getAvailableStock());
            assertEquals(75, response.getTotalStock());

            // Audit log
            Page<AuditLogResponse> logs = adminService.getAuditLogs(
                    AdminActionType.INVENTORY_ADJUSTED, null, PAGE);
            assertEquals(1, logs.getTotalElements());
            assertTrue(logs.getContent().get(0).getDetails().contains("ABSOLUTE"));
        }

        @Test
        @DisplayName("DELTA adjustment should increase availableStock only; totalStock stays unchanged")
        void adjustInventory_delta_shouldIncreaseAvailableStockOnly()
        {
            Product p = createProductWithInventory("Product A", 100.0, 30);

            AdjustInventoryRequest req = new AdjustInventoryRequest();
            req.setAdjustmentType(AdjustmentType.DELTA);
            req.setQuantity(20);

            InventoryResponse response = adminService.adjustInventory(p.getId(), req, ADMIN_EMAIL);

            // availableStock: 30 + 20 = 50
            assertEquals(50, response.getAvailableStock());
            // totalStock stays at the original seeded value (DELTA only touches availableStock)
            assertEquals(30, response.getTotalStock());
        }

        @Test
        @DisplayName("DELTA adjustment of 0 should be a no-op")
        void adjustInventory_deltaZero_shouldNotChangeStock()
        {
            Product p = createProductWithInventory("Product A", 100.0, 30);

            AdjustInventoryRequest req = new AdjustInventoryRequest();
            req.setAdjustmentType(AdjustmentType.DELTA);
            req.setQuantity(0);

            InventoryResponse response = adminService.adjustInventory(p.getId(), req, ADMIN_EMAIL);

            assertEquals(30, response.getAvailableStock());
            assertEquals(30, response.getTotalStock());
        }
    }

    // ── Admin Users ───────────────────────────────────────────────

    @Nested
    @DisplayName("Admin User Management")
    class AdminUserManagementTests
    {
        @Test
        @DisplayName("should create new admin user with ADMIN role and record audit log")
        void createAdminUser_shouldCreateUserWithAdminRoleAndAuditLog()
        {
            CreateAdminRequest req = new CreateAdminRequest();
            req.setEmail("newadmin@test.com");
            req.setPassword("securePass");
            req.setPhoneNumber("9876543210");

            adminService.createAdminUser(req, ADMIN_EMAIL);

            User created = userRepository.findByEmail("newadmin@test.com").orElseThrow();
            assertEquals(Role.ADMIN, created.getRole());

            Page<AuditLogResponse> logs = adminService.getAuditLogs(
                    AdminActionType.ADMIN_USER_CREATED, null, PAGE);
            assertEquals(1, logs.getTotalElements());
            assertEquals(ADMIN_EMAIL, logs.getContent().get(0).getAdminEmail());
            assertTrue(logs.getContent().get(0).getDetails().contains("newadmin@test.com"));
        }

        @Test
        @DisplayName("should throw BusinessException when email is already registered")
        void createAdminUser_duplicateEmail_shouldThrowBusinessException()
        {
            createUser("existing@test.com", Role.USER);

            CreateAdminRequest req = new CreateAdminRequest();
            req.setEmail("existing@test.com");
            req.setPassword("password123");

            assertThrows(BusinessException.class,
                    () -> adminService.createAdminUser(req, ADMIN_EMAIL));
        }
    }

    // ── Audit Logs ────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit Logs")
    class AuditLogTests
    {
        @Test
        @DisplayName("should filter audit logs by action type")
        void getAuditLogs_filterByActionType_shouldReturnMatchingLogs()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            Order o1 = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);
            Order o2 = createOrder(user, p, 1, OrderStatus.SHIPPED,   PaymentStatus.SUCCESS);

            // Two ORDER_STATUS_UPDATE actions
            UpdateOrderStatusRequest shipReq = new UpdateOrderStatusRequest();
            shipReq.setStatus(OrderStatus.SHIPPED);
            adminService.updateOrderStatus(o1.getId(), shipReq, ADMIN_EMAIL);

            UpdateOrderStatusRequest deliverReq = new UpdateOrderStatusRequest();
            deliverReq.setStatus(OrderStatus.DELIVERED);
            adminService.updateOrderStatus(o2.getId(), deliverReq, ADMIN_EMAIL);

            Page<AuditLogResponse> statusLogs = adminService.getAuditLogs(
                    AdminActionType.ORDER_STATUS_UPDATE, null, PAGE);
            assertEquals(2, statusLogs.getTotalElements());

            // No ORDER_CANCELLED logs
            Page<AuditLogResponse> cancelLogs = adminService.getAuditLogs(
                    AdminActionType.ORDER_CANCELLED, null, PAGE);
            assertEquals(0, cancelLogs.getTotalElements());
        }

        @Test
        @DisplayName("should filter audit logs by admin email")
        void getAuditLogs_filterByAdminEmail_shouldReturnMatchingLogs()
        {
            User user = createUser("user@test.com", Role.USER);
            Product p = createProductWithInventory("Product A", 100.0, 50);

            Order order = createOrder(user, p, 1, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS);

            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.SHIPPED);
            adminService.updateOrderStatus(order.getId(), req, "admin-alpha@test.com");

            AdjustInventoryRequest invReq = new AdjustInventoryRequest();
            invReq.setAdjustmentType(AdjustmentType.ABSOLUTE);
            invReq.setQuantity(100);
            adminService.adjustInventory(p.getId(), invReq, "admin-beta@test.com");

            Page<AuditLogResponse> alphaLogs = adminService.getAuditLogs(
                    null, "admin-alpha@test.com", PAGE);
            assertEquals(1, alphaLogs.getTotalElements());
            assertEquals(AdminActionType.ORDER_STATUS_UPDATE,
                    alphaLogs.getContent().get(0).getActionType());

            Page<AuditLogResponse> betaLogs = adminService.getAuditLogs(
                    null, "admin-beta@test.com", PAGE);
            assertEquals(1, betaLogs.getTotalElements());
            assertEquals(AdminActionType.INVENTORY_ADJUSTED,
                    betaLogs.getContent().get(0).getActionType());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private User createUser(String email, Role role)
    {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(role);
        return userRepository.save(user);
    }

    private Product createProductWithInventory(String name, double price, int stock)
    {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(stock);
        inventory.setAvailableStock(stock);
        inventory.setProduct(product);
        product.setInventory(inventory);

        return productRepository.save(product);
    }

    /**
     * Creates an order with one item. For non-CANCELLED orders, also decreases
     * inventory to simulate a real purchase (stock already consumed).
     */
    private Order createOrder(User user, Product product, int qty,
                              OrderStatus orderStatus, PaymentStatus paymentStatus)
    {
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(orderStatus);
        order.setPaymentStatus(paymentStatus);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(product.getPrice() * qty);
        order.setIdempotencyKey(UUID.randomUUID().toString());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setPriceAtPurchase(product.getPrice());
        order.getItems().add(item);

        // Simulate stock being consumed when the order was originally placed
        if (orderStatus != OrderStatus.CANCELLED) {
            productRepository.decreaseStockIfAvailable(product.getId(), qty);
        }

        return orderRepository.save(order);
    }

    private Payment createPayment(Order order, PaymentStatus paymentStatus)
    {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(order.getPaymentMethod());
        payment.setPaymentStatus(paymentStatus);
        payment.setAmount(order.getTotalAmount());
        return paymentRepository.save(payment);
    }
}
