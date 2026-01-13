package com.spring.ecommerce.cart;

import com.spring.ecommerce.user.User;
import com.spring.ecommerce.cart.dto.CartItemResponse;
import com.spring.ecommerce.cart.dto.CartResponse;
import com.spring.ecommerce.product.Product;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    public CartResponse addItem(User user, Product product, int quantity)
    {
        Cart cart = getOrCreateCartEntity(user);

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
        return mapToResponse(cartRepository.save(cart));
    }

    @Override
    public CartResponse removeItem(User user, Product product)
    {
        Cart cart = getOrCreateCartEntity(user);

        cart.getItems().removeIf(
            item -> item.getProduct().getId().equals(product.getId())
        );

        return mapToResponse(cartRepository.save(cart));
    }

    @Override
    public CartResponse getCart(User user) 
    {
        return mapToResponse(getOrCreateCartEntity(user));
    }

    // Helper methods
    
    private Cart getOrCreateCartEntity(User user) 
    {
        return cartRepository.findByUser(user)
                .orElseGet(() ->
                {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    private CartResponse mapToResponse(Cart cart)
    {
        CartResponse response = new CartResponse();
        response.setCartId(cart.getId());
        
        double total = 0;

        List<CartItemResponse> itemResponses = new ArrayList<>();

        for (CartItem item : cart.getItems())
        {
            CartItemResponse itemResponse = new CartItemResponse();
            itemResponse.setProductId(item.getProduct().getId());
            itemResponse.setProductName(item.getProduct().getName());
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setPrice(item.getProduct().getPrice());

            double subtotal = item.getQuantity() * item.getProduct().getPrice();
            itemResponse.setSubtotal(subtotal);

            total += subtotal;
            itemResponses.add(itemResponse);
        }
        
        response.setItems(itemResponses);
        response.setTotalAmount(total);
        
        return response;
    }
}
