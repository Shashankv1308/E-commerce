package com.spring.ecommerce.admin.dto;

import com.spring.ecommerce.order.PaymentStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePaymentStatusRequest 
{
    @NotNull(message = "Status is required")
    private PaymentStatus status;

    private String gatewayPaymentId;

    private String reason;
}
