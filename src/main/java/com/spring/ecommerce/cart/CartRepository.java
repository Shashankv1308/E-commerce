package com.spring.ecommerce.cart;

import com.spring.ecommerce.user.User;
import org.springframework.data.jpa.repository.JpaRepository; 

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long>
{
    Optional<Cart> findByUser(User user);
}
