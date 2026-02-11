package com.spring.ecommerce.order;

import com.spring.ecommerce.cart.Cart;
import com.spring.ecommerce.cart.CartItem;
import com.spring.ecommerce.cart.CartRepository;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.order.dto.OrderItemResponse;
import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.payment.Payment;
import com.spring.ecommerce.payment.PaymentRepository;
import com.spring.ecommerce.user.User;

import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OrderServiceImpl implements OrderService
{
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    public OrderServiceImpl(CartRepository cartRepository,
                            OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            ProductRepository productRepository)
    {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
    }

    @Override
    public OrderResponse placeOrder(User user, PaymentMethod paymentMethod)
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
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(paymentMethod);

        double total = 0;

        // 3. Convert CartItems to OrderItems
        for (CartItem cartItem : cart.getItems()) 
        {
            // Product product = cartItem.getProduct(); --> Use locks

            // re-fetch product WITH LOCK
            Product product = productRepository.lockById(
                        cartItem.getProduct().getId()
                        );

            Inventory inventory = product.getInventory();

            if(inventory.getAvailableStock() < cartItem.getQuantity())
            {
                throw new BusinessException(
                        "Insufficient stock for product: " + product.getName());
            }

            // Reserve stock
            inventory.setAvailableStock(                        // inventory is managed entity, so no need to save explicitly
                    inventory.getAvailableStock() - cartItem.getQuantity()
            );

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

        // 4. Save Order
        Order savedOrder = orderRepository.save(order);

        // 5. Create Payment
        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(total);

        paymentRepository.save(payment);

        // 6. Clear Cart
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToResponse(savedOrder);
    }

    @Override
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
    @Transactional // just to be explicit about transactionality, though class is already annotated.
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
            
            Inventory inventory = productRepository.lockById(
                        item.getProduct().getId()
                        ).getInventory(); // Lock product to prevent concurrent modifications.
            inventory.setAvailableStock(
                    inventory.getAvailableStock() + item.getQuantity()
            );
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
