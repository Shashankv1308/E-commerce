package com.spring.ecommerce.order;

public enum PaymentStatus 
{
    AWAITING_PAYMENT,  // Waiting for customer to complete online payment
    PENDING,           // Payment will be collected later (COD)
    SUCCESS,           // Payment confirmed
    FAILED,            // Payment failed/declined
    REFUNDED           // Payment was refunded
}
