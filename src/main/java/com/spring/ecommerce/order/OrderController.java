package com.spring.ecommerce.order;

import com.spring.ecommerce.order.dto.PlaceOrderRequest;
import com.spring.ecommerce.user.User;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.spring.ecommerce.order.dto.OrderResponse;

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
    public OrderResponse placeOrder(@RequestBody @Valid PlaceOrderRequest request) 
    {

        User user = new User();
        user.setId(1L);

        return orderService.placeOrder(user, request.getPaymentMethod());
    }
}
