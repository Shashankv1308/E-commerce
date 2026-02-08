package com.spring.ecommerce.order;

import com.spring.ecommerce.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;

import java.util.List;

import org.springframework.data.domain.Pageable;

public interface OrderRepository extends JpaRepository<Order, Long>
{
    List<Order> findByUser(User user);
    Page<Order> findByUser(User user, Pageable pageable);
}
