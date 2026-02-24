package com.spring.ecommerce.order;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.spring.ecommerce.cart.Cart;
import com.spring.ecommerce.cart.CartItem;
import com.spring.ecommerce.cart.CartRepository;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.Role;
import com.spring.ecommerce.user.User;
import com.spring.ecommerce.user.UserRepository;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
            transactionTemplate.execute(status -> {
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

    @Test
    void placeOrder_shouldReduceInventoryAndCreateOrder() {

        // Create User
        User user = new User();
        user.setEmail("test@test.com");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.USER);
        user = userRepository.save(user);

        // Create Product + Inventory
        Product product = new Product();
        product.setName("Test Product");
        product.setPrice(100.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(10);
        inventory.setAvailableStock(10);
        inventory.setProduct(product);
        product.setInventory(inventory);

        product = productRepository.save(product);

        // Create Cart + CartItem
        Cart cart = new Cart();
        cart.setUser(user);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(3);

        cart.getItems().add(item);
        cartRepository.save(cart);

        // Place Order
        OrderResponse response
                = orderService.placeOrder(user, PaymentMethod.ONLINE, "test-idempotency-key");

        // Assert inventory reduced
        Product updatedProduct
                = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(7, updatedProduct.getInventory().getAvailableStock());

        Cart updatedCart = cartRepository.findByUser(user).orElseThrow();
        assertTrue(updatedCart.getItems().isEmpty());

        // Assert order created
        assertNotNull(response.getOrderId());
        assertEquals(300.0, response.getTotalAmount());
    }

    @Test
    void cancelOrder_shouldRestoreInventoryAndUpdateStatus() {

        // Create User
        User user = new User();
        user.setEmail("cancel@test.com");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(Role.USER);
        user = userRepository.save(user);

        // Create Product with stock 10
        Product product = new Product();
        product.setName("Cancelable Product");
        product.setPrice(100.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(10);
        inventory.setAvailableStock(10);
        inventory.setProduct(product);
        product.setInventory(inventory);

        product = productRepository.save(product);

        //Create Cart with quantity 4
        Cart cart = new Cart();
        cart.setUser(user);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(4);

        cart.getItems().add(item);
        cartRepository.save(cart);

        // Place Order
        OrderResponse orderResponse
                = orderService.placeOrder(user, PaymentMethod.COD, "test");

        Long orderId = orderResponse.getOrderId();

        // Verify inventory reduced to 6
        Product afterOrder
                = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(6,
                afterOrder.getInventory().getAvailableStock());

        // Cancel Order
        OrderResponse cancelled
                = orderService.cancelOrder(orderId, user);

        // Verify inventory restored to 10
        Product afterCancel
                = productRepository.findById(product.getId()).orElseThrow();

        assertEquals(10,
                afterCancel.getInventory().getAvailableStock());

        // Verify order status updated
        assertEquals(OrderStatus.CANCELLED, cancelled.getOrderStatus());
        assertEquals(PaymentStatus.REFUNDED, cancelled.getPaymentStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void placeOrder_shouldNotOversell_underRealConcurrentRequests() throws Exception {
        // Create product with stock = 5
        Product product = new Product();
        product.setName("Concurrent Product");
        product.setPrice(100.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(5);
        inventory.setAvailableStock(5);
        inventory.setProduct(product);
        product.setInventory(inventory);

        product = productRepository.save(product);
        final Long productId = product.getId();

        // Create two users, tempUsers are added becuase Lambda requires captured variables to be 
        // effectively Final or Assigned only once, and we need to save the user to get the ID for the cart relationship.
        User tempUser1 = new User();
        tempUser1.setEmail("user1_" + System.nanoTime() + "@test.com");
        tempUser1.setPassword("password");
        tempUser1.setRole(Role.USER);
        final User user1 = userRepository.save(tempUser1);

        User tempUser2 = new User();
        tempUser2.setEmail("user2_" + System.nanoTime() + "@test.com");
        tempUser2.setPassword("password");
        tempUser2.setRole(Role.USER);
        final User user2 = userRepository.save(tempUser2);

        // Create separate carts (each wants full stock)
        createCart(user1, product, 5);
        createCart(user2, product, 5);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Boolean> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                orderService.placeOrder(user1, PaymentMethod.COD, UUID.randomUUID().toString());
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                orderService.placeOrder(user2, PaymentMethod.COD, UUID.randomUUID().toString());
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };

        Future<Boolean> f1 = executor.submit(task1);
        Future<Boolean> f2 = executor.submit(task2);

        readyLatch.await();
        startLatch.countDown();

        boolean r1 = f1.get(5, TimeUnit.SECONDS);
        boolean r2 = f2.get(5, TimeUnit.SECONDS);

        executor.shutdown();

        System.out.println("User1 success: " + r1);
        System.out.println("User2 success: " + r2);
        // Exactly one must succeed
        assertTrue(r1 ^ r2);

        // Inventory must be 0
        Product updated
                = productRepository.findById(productId).orElseThrow();

        assertEquals(0, updated.getInventory().getAvailableStock());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void placeOrder_shouldReturnSameOrder_whenSameIdempotencyKeyUsedSequentially() {

        User tempUser = new User();
        tempUser.setEmail("idempotent-seq-" + System.nanoTime() + "@test.com");
        tempUser.setPassword("password");
        tempUser.setRole(Role.USER);
        final User user = userRepository.save(tempUser);

        // Create Product with stock = 10
        Product product = new Product();
        product.setName("Idempotent Product");
        product.setPrice(50.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(10);
        inventory.setAvailableStock(10);
        inventory.setProduct(product);
        product.setInventory(inventory);

        product = productRepository.save(product);
        final Long productId = product.getId();

        // Create Cart with quantity 2
        createCart(user, product, 2);

        String idempotencyKey = "sequential-idempotency-key-" + UUID.randomUUID();

        // First call
        OrderResponse firstResponse = orderService.placeOrder(user, PaymentMethod.ONLINE, idempotencyKey);
        assertNotNull(firstResponse.getOrderId());
        assertEquals(100.0, firstResponse.getTotalAmount());

        // Add to cart for second call
        createCart(user, product, 3);

        // Second call with same idempotency key - should return same order (not create new one)
        OrderResponse secondResponse = orderService.placeOrder(user, PaymentMethod.ONLINE, idempotencyKey);

        // Both responses should have the same order ID
        assertEquals(firstResponse.getOrderId(), secondResponse.getOrderId());
        assertEquals(firstResponse.getTotalAmount(), secondResponse.getTotalAmount());

        // 10 - 2 = 8
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(8, updatedProduct.getInventory().getAvailableStock());

        Product updatedProduct2 = productRepository.findById(productId).orElseThrow();
        assertEquals(8, updatedProduct2.getInventory().getAvailableStock());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void placeOrder_shouldReturnSameOrder_whenSameIdempotencyKeyUsedConcurrently() throws Exception {

        User tempUser = new User();
        tempUser.setEmail("idempotent-concurrent@test.com");
        tempUser.setPassword("password");
        tempUser.setRole(Role.USER);
        final User user = userRepository.save(tempUser);

        // Create Product with stock = 10
        Product product = new Product();
        product.setName("Concurrent Idempotent Product");
        product.setPrice(75.0);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(10);
        inventory.setAvailableStock(10);
        inventory.setProduct(product);
        product.setInventory(inventory);

        product = productRepository.save(product);
        final Long productId = product.getId();

        // Create Cart with quantity 3
        createCart(user, product, 3);

        final String idempotencyKey = "concurrent-idempotency-key-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<OrderResponse> task = () -> {
            readyLatch.countDown();
            startLatch.await();
            return orderService.placeOrder(user, PaymentMethod.COD, idempotencyKey);
        };

        Future<OrderResponse> f1 = executor.submit(task);
        Future<OrderResponse> f2 = executor.submit(task);

        // Wait for both threads to be ready
        readyLatch.await();
        // Release both threads simultaneously
        startLatch.countDown();

        OrderResponse r1 = f1.get(5, TimeUnit.SECONDS);
        OrderResponse r2 = f2.get(5, TimeUnit.SECONDS);

        executor.shutdown();

        // Both responses should return the same order
        assertNotNull(r1.getOrderId());
        assertNotNull(r2.getOrderId());
        assertEquals(r1.getOrderId(), r2.getOrderId());
        assertEquals(r1.getTotalAmount(), r2.getTotalAmount());

        // Inventory should only be reduced once (10 - 3 = 7)
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(7, updatedProduct.getInventory().getAvailableStock());
    }

    // Helper method - creates a new cart or uses existing one (wrapped in transaction)
    private void createCart(User user, Product product, int quantity) {
        transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByUser(user).orElseGet(() -> {
                Cart newCart = new Cart();
                newCart.setUser(user);
                return newCart;
            });

            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);

            cart.getItems().add(item);
            cartRepository.save(cart);
            return null;
        });
    }
}