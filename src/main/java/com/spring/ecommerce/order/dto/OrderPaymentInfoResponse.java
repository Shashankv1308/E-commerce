package com.spring.ecommerce.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderPaymentInfoResponse {
    private Long orderId;
    private String paymentStatus;
    private String paymentLink;
    private String gatewayOrderId;
    private boolean ready;
}
