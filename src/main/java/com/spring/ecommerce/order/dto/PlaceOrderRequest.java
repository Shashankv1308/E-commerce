package com.spring.ecommerce.order.dto;

import com.spring.ecommerce.order.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceOrderRequest 
{
    @NotNull
    private PaymentMethod paymentMethod;
}
