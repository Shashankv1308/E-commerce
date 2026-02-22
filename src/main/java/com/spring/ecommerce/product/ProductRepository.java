package com.spring.ecommerce.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface ProductRepository extends JpaRepository<Product, Long>
{
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableStock = i.availableStock - :qty
        WHERE i.product.id = :productId
        AND i.availableStock >= :qty
    """)
    int decreaseStockIfAvailable(
            @Param("productId") Long productId,
            @Param("qty") int qty
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableStock = i.availableStock + :qty
        WHERE i.product.id = :productId
    """)
    int increaseStock(
            @Param("productId") Long productId,
            @Param("qty") int qty
    );
    List<Product> findByIsActiveTrue();
}
