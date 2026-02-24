package com.spring.ecommerce.order;

import com.spring.ecommerce.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

public interface OrderRepository extends JpaRepository<Order, Long>
{
    List<Order> findByUser(User user);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    Page<Order> findByUser(User user, Pageable pageable);

    Page<Order> findByUserAndOrderStatus(
            User user, OrderStatus orderStatus, Pageable pageable
    );

    Page<Order> findByUserAndPaymentStatus(
            User user, PaymentStatus paymentStatus, Pageable pageable
    );

    Page<Order> findByUserAndOrderStatusAndPaymentStatus(
            User user,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus,
            Pageable pageable
    );
}
