package com.spring.ecommerce.product;

import com.spring.ecommerce.exception.ResourceNotFoundException;
import com.spring.ecommerce.product.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService
{
    private final ProductRepository productRepository;

    @Override
    @Cacheable(value = "products", key = "'active'")
    public List<ProductResponse> getAllActiveProducts()
    {
        return productRepository.findByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id)
    {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return toResponse(product);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#productId"),
        @CacheEvict(value = "products", key = "'active'")
    })
    public void evictProductCache(Long productId)
    {
        // triggered by admin metadata changes only (name/price/description/isActive)
        // stock changes are NOT evicted — 5-min TTL handles stock staleness
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void evictAllProductCaches()
    {
        // triggered only for bulk admin operations
    }

    private ProductResponse toResponse(Product product)
    {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setAvailableStock(
                product.getInventory() != null
                        ? product.getInventory().getAvailableStock()
                        : 0
        );
        return response;
    }
}
