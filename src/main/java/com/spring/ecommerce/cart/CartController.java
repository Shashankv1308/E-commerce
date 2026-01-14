package com.spring.ecommerce.cart;

import com.spring.ecommerce.cart.dto.AddToCartRequest;
import com.spring.ecommerce.cart.dto.CartResponse;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController 
{
    private final CartService cartService;
    private final ProductRepository productRepository;

    public CartController(CartService cartService, ProductRepository productRepository) 
    {
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    @PostMapping("/items")
    public CartResponse addItem(@RequestBody @Valid AddToCartRequest request)
    {
        User user = new User();
        user.setId(1L);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return cartService.addItem(user, product, request.getQuantity());
    }


    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId) 
    {
        User user = new User();
        user.setId(1L);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return cartService.removeItem(user, product);
    }
}
