package com.spring.ecommerce.payment.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event published when payment is successfully completed via gateway callback.
 */
@Getter
@AllArgsConstructor
public class PaymentCompletedEvent 
{
    private final Long orderId;
    private final Long paymentId;
    private final String gatewayPaymentId;
    private final String gatewayOrderId;
}
