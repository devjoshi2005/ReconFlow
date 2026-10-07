package com.reconflow.security;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class SecurityAuditAspect {

    @AfterThrowing(pointcut = "within(com.reconflow.controller..*)", throwing = "ex")
    public void logAccessDenied(AccessDeniedException ex) {
        String user = SecurityUtils.getCurrentUserRole() != null 
                ? SecurityUtils.getCurrentUserRole() 
                : "Anonymous";
        String userId = SecurityUtils.getCurrentUserId() != null 
                ? SecurityUtils.getCurrentUserId().toString() 
                : "Unknown";
                
        log.warn("SECURITY AUDIT: 403 Forbidden violation attempt detected. UserID: {}, Role: {}, Error: {}", 
                 userId, user, ex.getMessage());
    }
}
