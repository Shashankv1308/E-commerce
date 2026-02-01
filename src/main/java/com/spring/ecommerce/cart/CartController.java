package com.spring.ecommerce.cart;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.ecommerce.cart.dto.AddToCartRequest;
import com.spring.ecommerce.cart.dto.CartResponse;
import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.security.CustomUserDetails;
import com.spring.ecommerce.user.User;

import jakarta.validation.Valid;

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
    public CartResponse addItem(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid AddToCartRequest request)
    {
        User user = userDetails.getUser();
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return cartService.addItem(user, product, request.getQuantity());
    }


    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long productId) 
    {
        User user = userDetails.getUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return cartService.removeItem(user, product);
    }
}
