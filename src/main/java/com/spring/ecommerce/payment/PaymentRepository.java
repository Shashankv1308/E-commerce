package com.spring.ecommerce.payment;

import com.spring.ecommerce.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>
{
    Optional<Payment> findByOrder(Order order);
}
