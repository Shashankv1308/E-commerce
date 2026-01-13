package com.spring.ecommerce.order;

import com.spring.ecommerce.user.User;
import com.spring.ecommerce.order.dto.OrderResponse;

public interface OrderService 
{
    OrderResponse placeOrder(User user, PaymentMethod paymentMethod);
}
