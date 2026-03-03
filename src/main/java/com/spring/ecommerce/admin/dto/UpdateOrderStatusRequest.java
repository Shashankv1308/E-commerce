package com.spring.ecommerce.admin.dto;

import com.spring.ecommerce.order.OrderStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOrderStatusRequest 
{
    @NotNull(message = "Status is required")
    private OrderStatus status;
}
