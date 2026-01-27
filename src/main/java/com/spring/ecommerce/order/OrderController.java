package com.spring.ecommerce.order;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.order.dto.PlaceOrderRequest;
import com.spring.ecommerce.security.CustomUserDetails;
import com.spring.ecommerce.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController 
{

    private final OrderService orderService;

    public OrderController(OrderService orderService) 
    {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid PlaceOrderRequest request) 
    {

        User user = userDetails.getUser();

        return orderService.placeOrder(user, request.getPaymentMethod());
    }
}
