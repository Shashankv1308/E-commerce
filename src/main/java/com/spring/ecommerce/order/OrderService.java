package com.spring.ecommerce.order;

import com.spring.ecommerce.user.User;

public interface OrderService 
{
    Order placeOrder(User user, PaymentMethod paymentMethod);
}
