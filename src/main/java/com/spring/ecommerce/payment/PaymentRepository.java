package com.spring.ecommerce.payment;

import com.spring.ecommerce.order.Order;
import com.spring.ecommerce.order.PaymentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>
{
    Optional<Payment> findByOrder(Order order);
    
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    
    Optional<Payment> findByOrderId(Long orderId);

    /** Pessimistic lock for race-safe payment initiation */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    /** Find stale payments for scheduled reconciliation */
    List<Payment> findByPaymentStatusAndCreatedAtBefore(
            PaymentStatus paymentStatus, LocalDateTime cutoff);

    /** Admin: paginated payment queries */
    Page<Payment> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);
}
