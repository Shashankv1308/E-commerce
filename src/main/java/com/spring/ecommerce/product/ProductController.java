package com.spring.ecommerce.product;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spring.ecommerce.product.dto.ProductResponse;

@RestController
@RequestMapping("/products")
public class ProductController
{
    private final ProductService productService;

    public ProductController(ProductService productService)
    {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> getAllActiveProducts()
    {
        return productService.getAllActiveProducts();
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id)
    {
        return productService.getProductById(id);
    }
}

