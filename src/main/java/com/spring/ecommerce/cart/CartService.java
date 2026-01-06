package com.spring.ecommerce.cart;

import com.spring.ecommerce.user.User;
import com.spring.ecommerce.product.Product;;

public interface CartService 
{
    Cart getOrCreateCart(User user);

    Cart addItem(User user, Product product, int quantity);

    Cart removeItem(User user, Product product);
}
