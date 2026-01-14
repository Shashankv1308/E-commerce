package com.spring.ecommerce.cart;

import com.spring.ecommerce.user.User;
import com.spring.ecommerce.cart.dto.CartResponse;
import com.spring.ecommerce.product.Product;;

public interface CartService 
{
    CartResponse addItem(User user, Product product, int quantity);

    CartResponse removeItem(User user, Product product);

    CartResponse getCart(User user);
}
