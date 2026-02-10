package com.spring.ecommerce.order;

import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService 
{
    OrderResponse placeOrder(User user, PaymentMethod paymentMethod);

    OrderResponse getOrderById(Long orderId, User user);

    Page<OrderResponse> getOrderHistory(
            User user,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    OrderResponse cancelOrder(Long orderId, User user);
}
