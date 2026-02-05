package com.spring.ecommerce.order;

import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.user.User;

public interface OrderService 
{
    OrderResponse placeOrder(User user, PaymentMethod paymentMethod);

    OrderResponse getOrderById(Long orderId, User user);
}
