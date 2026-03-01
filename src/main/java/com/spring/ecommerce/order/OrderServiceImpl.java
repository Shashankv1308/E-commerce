package com.spring.ecommerce.order;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spring.ecommerce.cart.Cart;
import com.spring.ecommerce.cart.CartItem;
import com.spring.ecommerce.cart.CartRepository;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.order.dto.OrderItemResponse;
import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.payment.Payment;
import com.spring.ecommerce.payment.PaymentRepository;
import com.spring.ecommerce.payment.event.OrderPlacedEvent;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class OrderServiceImpl implements OrderService
{
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final ObjectProvider<OrderServiceImpl> selfProvider;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderServiceImpl(CartRepository cartRepository,
                            OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            ProductRepository productRepository,
                            ObjectProvider<OrderServiceImpl> selfProvider,
                            ApplicationEventPublisher eventPublisher)
    {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
        // Store the provider, don't resolve yet — bean isn't in context during construction
        this.selfProvider = selfProvider;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Non-transactional wrapper.
     * Calls selfProvider.getObject().createOrder(), which runs in its own @Transactional boundary.
     * ObjectProvider resolves lazily here (after bean is fully initialized), so we get the proxy.
     * Because the inner transaction commits before returning here, any
     * DataIntegrityViolationException (duplicate idempotency key) is catchable.
     */
    @Override
    public OrderResponse placeOrder(User user, PaymentMethod paymentMethod, String idempotencyKey)
    {
        try {
            return selfProvider.getObject().createOrder(user, paymentMethod, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request already committed an order with the same key.
            // The @EntityGraph on this repo method eagerly loads items + products
            // so mapToResponse can traverse them without an active Hibernate session.
            return orderRepository.findByUserAndIdempotencyKey(user, idempotencyKey)
                    .map(this::mapToResponse)
                    .orElseThrow(() -> new BusinessException("Order placement failed due to conflict"));
        }
    }

    @Transactional
    public OrderResponse createOrder(User user, PaymentMethod paymentMethod, String idempotencyKey)
    {
        // 1. Fetch Cart
        Cart cart = cartRepository.findByUser(user)
                        .orElseThrow(() -> new BusinessException("Cart is empty"));

        if (cart.getItems().isEmpty()) 
        {
            throw new BusinessException("Cart has no items");
        }

        // 2. Create Order
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        // Set initial payment status based on payment method
        PaymentStatus initialPaymentStatus = (paymentMethod == PaymentMethod.ONLINE) 
                ? PaymentStatus.AWAITING_PAYMENT 
                : PaymentStatus.PENDING;
        order.setPaymentStatus(initialPaymentStatus);
        order.setPaymentMethod(paymentMethod);
        order.setIdempotencyKey(idempotencyKey);

        double total = 0;

        // 3. Convert CartItems to OrderItems
        for (CartItem cartItem : cart.getItems()) 
        {
            Product product = cartItem.getProduct();

            // Atomic Update
            int updated = productRepository.decreaseStockIfAvailable(
                    product.getId(),
                    cartItem.getQuantity()
            );

            if (updated == 0) {
                throw new BusinessException(
                        "Insufficient stock for product: " + product.getName()
                );
            }

            // Create OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtPurchase(product.getPrice());
            
            order.getItems().add(orderItem);

            total += cartItem.getQuantity() * product.getPrice();
        }

        order.setTotalAmount(total);

        // Save + flush: forces the INSERT now so the unique constraint violation
        // (if any) is thrown inside this transaction, not silently at commit time.
        Order savedOrder = orderRepository.save(order);
        entityManager.flush();

        // 5. Create Payment with same initial status as order
        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(initialPaymentStatus);
        payment.setAmount(total);

        Payment savedPayment = paymentRepository.save(payment);

        // 6. Clear Cart
        cart.getItems().clear();
        cartRepository.save(cart);

        // 7. Publish OrderPlacedEvent for async payment processing (ONLINE only)
        // COD orders don't need gateway initiation — they stay PENDING until delivery
        // Event will be delivered AFTER transaction commits (via @TransactionalEventListener)
        if (paymentMethod == PaymentMethod.ONLINE) {
            eventPublisher.publishEvent(new OrderPlacedEvent(
                    savedOrder.getId(),
                    savedPayment.getId(),
                    user.getId(),
                    total,
                    paymentMethod,
                    user.getEmail(),
                    user.getPhoneNumber()
            ));
        }

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse getOrderById(Long orderId, User user)
    {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found")
                );

        // OWNERSHIP CHECK
        if (!order.getUser().getId().equals(user.getId())) 
        {
            throw new BusinessException("You are not allowed to access this order");
        }

        return mapToResponse(order);
    }

    @Override
    @Transactional
    public Page<OrderResponse> getOrderHistory(
            User user,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            Pageable pageable
    ) {

        Page<Order> orders;

        if (orderStatus != null && paymentStatus != null) {
            orders = orderRepository
                    .findByUserAndOrderStatusAndPaymentStatus(
                            user, orderStatus, paymentStatus, pageable
                    );
        } else if (orderStatus != null) {
            orders = orderRepository
                    .findByUserAndOrderStatus(user, orderStatus, pageable);
        } else if (paymentStatus != null) {
            orders = orderRepository
                    .findByUserAndPaymentStatus(user, paymentStatus, pageable);
        } else {
            orders = orderRepository.findByUser(user, pageable);
        }

        return orders.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, User user) 
    {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found")
                );

        // Ownership check
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BusinessException("You are not allowed to cancel this order");
        }

        // State validation
        if (order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(
                    "Order cannot be cancelled in state: " + order.getOrderStatus()
            );
        }

        // Restore inventory
        for (OrderItem item : order.getItems()) {
            
            int updated = productRepository.increaseStock(
                    item.getProduct().getId(),
                    item.getQuantity()
            );

            if (updated == 0) {
                throw new BusinessException(
                        "Failed to restore inventory for product: "
                        + item.getProduct().getName()
                );
            }
        }

        // Update order & payment status
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);

        // Managed entity → no explicit save required
        return mapToResponse(order);
    }

    //Helper method
    private OrderResponse mapToResponse(Order order) 
    {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderStatus(order.getOrderStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());

        List<OrderItemResponse> itemResponses = new ArrayList<>();

        for (OrderItem item : order.getItems()) 
        {
            OrderItemResponse itemResponse = new OrderItemResponse();
            itemResponse.setProductId(item.getProduct().getId());
            itemResponse.setProductName(item.getProduct().getName());
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setPriceAtPurchase(item.getPriceAtPurchase());

            double subtotal = item.getQuantity() * item.getPriceAtPurchase();
            itemResponse.setSubtotal(subtotal);

            itemResponses.add(itemResponse);
        }

        response.setItems(itemResponses);

        return response;
    }
}