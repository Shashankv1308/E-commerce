package com.spring.ecommerce.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> 
{
    @EntityGraph(attributePaths = {"product"})
    Page<Inventory> findAll(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
        SET i.availableStock = :stock, i.totalStock = :stock
        WHERE i.product.id = :productId
    """)
    int setStock(@Param("productId") Long productId, @Param("stock") int stock);
}
