package com.spring.ecommerce.cart;

import com.spring.ecommerce.user.User;
import com.spring.ecommerce.product.Product;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CartServiceImpl implements CartService
{
    private final CartRepository cartRepository;

    public CartServiceImpl(CartRepository cartRepository) 
    {
        this.cartRepository = cartRepository;
    }

    @Override
    public Cart getOrCreateCart(User user) 
    {
        return cartRepository.findByUser(user)
                .orElseGet(() ->
                {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    @Override
    public Cart addItem(User user, Product product, int quantity)
    {
        Cart cart = getOrCreateCart(user);

        cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .ifPresentOrElse(
                    item -> item.setQuantity(item.getQuantity() + quantity),
                    () -> {
                        CartItem item = new CartItem();
                        item.setCart(cart);
                        item.setProduct(product);
                        item.setQuantity(quantity);
                        cart.getItems().add(item);
                    }
                );
        return cartRepository.save(cart);
    }

    @Override
    public Cart removeItem(User user, Product product)
    {
        Cart cart = getOrCreateCart(user);

        cart.getItems().removeIf(
            item -> item.getProduct().getId().equals(product.getId())
        );

        return cartRepository.save(cart);
    }
}
