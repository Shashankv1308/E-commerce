package com.spring.ecommerce.admin.dto;

import java.time.LocalDateTime;

import com.spring.ecommerce.admin.AdminActionType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogResponse 
{
    private Long id;
    private String adminEmail;
    private AdminActionType actionType;
    private String targetEntity;
    private Long targetId;
    private String details;
    private LocalDateTime createdAt;
}
