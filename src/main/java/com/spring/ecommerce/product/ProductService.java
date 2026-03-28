package com.spring.ecommerce.product;

import com.spring.ecommerce.product.dto.ProductResponse;

import java.util.List;

public interface ProductService
{
    List<ProductResponse> getAllActiveProducts();

    ProductResponse getProductById(Long id);

    /**
     * Evict cache for a specific product + the active-products list.
     * Called only when admin changes product metadata (name, price, description, isActive).
     * NOT called on stock changes — TTL handles stock staleness.
     */
    void evictProductCache(Long productId);

    /**
     * Evict entire products cache.
     * Called only for bulk admin operations (e.g., bulk deactivate).
     */
    void evictAllProductCaches();
}
