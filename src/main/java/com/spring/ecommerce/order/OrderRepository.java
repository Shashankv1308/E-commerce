package com.spring.ecommerce.order;

import com.spring.ecommerce.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long>
{
    List<Order> findByUser(User user);
}
