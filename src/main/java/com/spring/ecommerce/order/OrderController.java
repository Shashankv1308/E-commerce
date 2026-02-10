package com.spring.ecommerce.order;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spring.ecommerce.order.dto.OrderResponse;
import com.spring.ecommerce.order.dto.PlaceOrderRequest;
import com.spring.ecommerce.security.CustomUserDetails;
import com.spring.ecommerce.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping
    public Page<OrderResponse> getOrderHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            Pageable pageable
    ) {
        return orderService.getOrderHistory(
                userDetails.getUser(),
                orderStatus,
                paymentStatus,
                pageable
        );
    }


    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return orderService.getOrderById(orderId, userDetails.getUser());
    }

    @PostMapping
    public OrderResponse placeOrder(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid PlaceOrderRequest request) 
    {

        User user = userDetails.getUser();

        return orderService.placeOrder(user, request.getPaymentMethod());
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return orderService.cancelOrder(orderId, userDetails.getUser());
    }
}
