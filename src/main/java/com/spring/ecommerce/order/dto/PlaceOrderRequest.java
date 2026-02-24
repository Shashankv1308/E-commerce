package com.spring.ecommerce.order.dto;

import com.spring.ecommerce.order.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceOrderRequest 
{
    @NotNull
    private PaymentMethod paymentMethod;

    @NotBlank
    @Size(max = 64)
    private String idempotencyKey;
}
