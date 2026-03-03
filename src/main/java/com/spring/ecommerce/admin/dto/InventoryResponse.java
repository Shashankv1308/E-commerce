package com.spring.ecommerce.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryResponse 
{
    private Long productId;
    private String productName;
    private Integer availableStock;
    private Integer totalStock;
    private boolean isActive;
}
