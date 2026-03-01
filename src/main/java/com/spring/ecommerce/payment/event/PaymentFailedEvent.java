package com.spring.ecommerce.payment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event published when payment fails via gateway callback.
 * Triggers saga compensation (inventory restoration, order cancellation).
 */
@Getter
@AllArgsConstructor
public class PaymentFailedEvent 
{
    private final Long orderId;
    private final Long paymentId;
    private final String failureReason;
}
