package com.spring.ecommerce.payment.event;

import com.spring.ecommerce.order.PaymentMethod;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event published after an order is successfully created and committed.
 * Triggers async payment initiation for ONLINE payment methods.
 */
@Getter
@AllArgsConstructor
public class OrderPlacedEvent 
{
    private final Long orderId;
    private final Long paymentId;
    private final Long userId;
    private final Double amount;
    private final PaymentMethod paymentMethod;
    private final String customerEmail;
    private final String customerPhone;
}
