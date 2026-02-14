package com.spring.ecommerce.config;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.spring.ecommerce.inventory.Inventory;
import com.spring.ecommerce.product.Product;
import com.spring.ecommerce.product.ProductRepository;
import com.spring.ecommerce.user.Role;
import com.spring.ecommerce.user.User;
import com.spring.ecommerce.user.UserRepository;


@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {

            // ---- USERS ----
            if (userRepository.findByEmail("user@test.com").isEmpty()) 
            {
                User user = new User();
                user.setEmail("user@test.com");
                user.setPassword(passwordEncoder.encode("password"));
                user.setRole(Role.USER);
                userRepository.save(user);
            }

            if (userRepository.findByEmail("admin@test.com").isEmpty()) 
            {
                User admin = new User();
                admin.setEmail("admin@test.com");
                admin.setPassword(passwordEncoder.encode("password"));
                admin.setRole(Role.ADMIN);
                userRepository.save(admin);
            }

            // ---- PRODUCT + INVENTORY ----
            if (productRepository.findByIsActiveTrue().isEmpty())
            {
                Product product = new Product();
                product.setName("Test Product");
                product.setPrice(100.0);
                product.setActive(true);

                Inventory inventory = new Inventory();
                inventory.setTotalStock(100);
                inventory.setAvailableStock(100);

                // Bidirectional wiring
                inventory.setProduct(product);
                product.setInventory(inventory);

                productRepository.save(product);
            }
        };
    }
}