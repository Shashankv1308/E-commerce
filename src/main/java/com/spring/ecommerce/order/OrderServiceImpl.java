package com.spring.ecommerce.order;

import com.spring.ecommerce.cart.Cart;
import com.spring.ecommerce.cart.CartItem;
import com.spring.ecommerce.cart.CartRepository;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.payment.Payment;
import com.spring.ecommerce.payment.PaymentRepository;
import com.spring.ecommerce.user.User;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OrderServiceImpl implements OrderService
{
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderServiceImpl(CartRepository cartRepository,
                            OrderRepository orderRepository,
                            PaymentRepository paymentRepository)
    {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Order placeOrder(User user, PaymentMethod paymentMethod)
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
        order.setOrderstatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(paymentMethod);

        double total = 0;

        // 3. Convert CartItems to OrderItems
        for (CartItem cartItem : cart.getItems()) 
        {
            Product product = cartItem.getProduct();
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

        return savedOrder;
    }
}
