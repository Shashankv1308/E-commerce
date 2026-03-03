package com.spring.ecommerce.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> 
{
    Page<AdminAuditLog> findByActionType(AdminActionType actionType, Pageable pageable);

    Page<AdminAuditLog> findByAdminEmail(String adminEmail, Pageable pageable);

    Page<AdminAuditLog> findByTargetEntityAndTargetId(String targetEntity, Long targetId, Pageable pageable);
}
