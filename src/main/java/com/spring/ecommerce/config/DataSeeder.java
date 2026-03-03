package com.spring.ecommerce.config;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.spring.ecommerce.inventory.Inventory;
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


@Profile("!test")
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {

            // ── Users ────────────────────────────────────────────────────

            if (userRepository.findByEmail("user@test.com").isEmpty())
            {
                User user = new User();
                user.setEmail("user@test.com");
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(Role.USER);
                userRepository.save(user);
            }

            if (userRepository.findByEmail("user2@test.com").isEmpty())
            {
                User user2 = new User();
                user2.setEmail("user2@test.com");
                user2.setPassword(passwordEncoder.encode("password"));
                user2.setRole(Role.USER);
                userRepository.save(user2);
            }

            if (userRepository.findByEmail("admin@test.com").isEmpty())
            {
                User admin = new User();
                admin.setEmail("admin@test.com");
                admin.setPassword(passwordEncoder.encode("password"));
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }

            // ── Products + Inventory ─────────────────────────────────────
            //
            // Only seed products and orders when no orders exist yet (idempotent).
            // Stock values reflect sold quantities from the seeded orders below.
            //
            //  Product              totalStock  availableStock  Notes
            //  ─────────────────    ──────────  ──────────────  ───────────────────────────────────
            //  Test Product             100           98        Order-1 sold 2 units
            //  Wireless Headphones       50           49        Order-2 sold 1 unit
            //  USB-C Charger            200          197        Order-3 (pending payment) sold 3
            //  Laptop Stand              20            0        Out of stock — good for restock test
            //  Galaxy Buds               30           30        Order-5 cancelled, stock restored
            //

            if (orderRepository.count() == 0)
            {
                User user  = userRepository.findByEmail("user@test.com").orElseThrow();
                User user2 = userRepository.findByEmail("user2@test.com").orElseThrow();

                // -- Products --
                Product testProduct = saveProduct(productRepository,
                        "Test Product", 100.0, 100, 98);

                Product headphones = saveProduct(productRepository,
                        "Wireless Headphones", 250.0, 50, 49);

                Product charger = saveProduct(productRepository,
                        "USB-C Charger", 30.0, 200, 197);

                // Laptop Stand seeded with 0 availableStock for admin restock testing
                saveProduct(productRepository, "Laptop Stand", 75.0, 20, 0);

                Product galaxyBuds = saveProduct(productRepository,
                        "Galaxy Buds", 180.0, 30, 30);

                // -- Orders --

                // Order-1: CONFIRMED / SUCCESS → admin can ship this
                Order order1 = saveOrder(orderRepository, user, testProduct,
                        2, OrderStatus.CONFIRMED, PaymentStatus.SUCCESS, "seed-order-001");
                savePayment(paymentRepository, order1, PaymentStatus.SUCCESS,
                        "seed_gw_001", "seed_pay_001", null);

                // Order-2: SHIPPED / SUCCESS → admin can mark as delivered
                Order order2 = saveOrder(orderRepository, user, headphones,
                        1, OrderStatus.SHIPPED, PaymentStatus.SUCCESS, "seed-order-002");
                savePayment(paymentRepository, order2, PaymentStatus.SUCCESS,
                        "seed_gw_002", "seed_pay_002", null);

                // Order-3: CONFIRMED / AWAITING_PAYMENT → admin can manage payment
                Order order3 = saveOrder(orderRepository, user2, charger,
                        3, OrderStatus.CONFIRMED, PaymentStatus.AWAITING_PAYMENT, "seed-order-003");
                savePayment(paymentRepository, order3, PaymentStatus.AWAITING_PAYMENT,
                        null, null, null);

                // Order-4: CANCELLED / REFUNDED → completed lifecycle, nothing more to do
                Order order4 = saveOrder(orderRepository, user, testProduct,
                        1, OrderStatus.CANCELLED, PaymentStatus.REFUNDED, "seed-order-004");
                savePayment(paymentRepository, order4, PaymentStatus.REFUNDED,
                        "seed_gw_004", "seed_pay_004", null);

                // Order-5: CANCELLED / FAILED → payment failed, inventory restored
                Order order5 = saveOrder(orderRepository, user2, galaxyBuds,
                        2, OrderStatus.CANCELLED, PaymentStatus.FAILED, "seed-order-005");
                savePayment(paymentRepository, order5, PaymentStatus.FAILED,
                        "seed_gw_005", null, "Card declined by bank");

            }
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Product saveProduct(ProductRepository repo,
                                String name, double price,
                                int totalStock, int availableStock)
    {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setActive(true);

        Inventory inventory = new Inventory();
        inventory.setTotalStock(totalStock);
        inventory.setAvailableStock(availableStock);
        inventory.setProduct(product);
        product.setInventory(inventory);

        return repo.save(product);
    }

    private Order saveOrder(OrderRepository repo,
                            User user, Product product, int qty,
                            OrderStatus orderStatus, PaymentStatus paymentStatus,
                            String idempotencyKey)
    {
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(orderStatus);
        order.setPaymentStatus(paymentStatus);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(product.getPrice() * qty);
        order.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setPriceAtPurchase(product.getPrice());
        order.getItems().add(item);

        return repo.save(order);
    }

    private Payment savePayment(PaymentRepository repo,
                                Order order, PaymentStatus paymentStatus,
                                String gatewayOrderId, String gatewayPaymentId,
                                String failureReason)
    {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(order.getPaymentMethod());
        payment.setPaymentStatus(paymentStatus);
        payment.setAmount(order.getTotalAmount());
        payment.setGatewayOrderId(gatewayOrderId);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setFailureReason(failureReason);
        return repo.save(payment);
    }
}