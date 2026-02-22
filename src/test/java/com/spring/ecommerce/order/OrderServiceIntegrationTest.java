package com.spring.ecommerce.order;

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

    // @Autowired
    // private EntityManager entityManager;

    // @Autowired
    // private TransactionTemplate transactionTemplate;

    // @BeforeEach
    // void cleanDatabase() {
    //         transactionTemplate.execute(status -> {
    //             entityManager.createNativeQuery("DELETE FROM order_items").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM payments").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM cart_items").executeUpdate(); 
    //             entityManager.createNativeQuery("DELETE FROM orders").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM carts").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM inventory").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM products").executeUpdate();
    //             entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
    //             return null;
    //         });
    // }

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
                = orderService.placeOrder(user, PaymentMethod.ONLINE);

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
                = orderService.placeOrder(user, PaymentMethod.COD);

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
                orderService.placeOrder(user1, PaymentMethod.COD);
                return true;
            } catch (BusinessException e) {
                return false;
            }
        };

        Callable<Boolean> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                orderService.placeOrder(user2, PaymentMethod.COD);
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

    // Hepler method
    private void createCart(User user, Product product, int quantity) {
        Cart cart = new Cart();
        cart.setUser(user);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);

        cart.getItems().add(item);
        cartRepository.save(cart);
    }
}
