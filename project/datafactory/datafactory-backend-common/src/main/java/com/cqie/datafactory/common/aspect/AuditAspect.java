package com.cqie.datafactory.common.aspect;

import com.cqie.datafactory.common.annotation.Auditable;
import com.cqie.datafactory.common.entity.AuditLog;
import com.cqie.datafactory.common.mapper.AuditLogMapper;
import com.cqie.datafactory.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
@Aspect
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogMapper auditLogMapper;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Instant start = Instant.now();
        AuditLog auditLog = new AuditLog();
        auditLog.setOperation(auditable.value());
        auditLog.setStatus(1);

        try {
            // 获取当前用户
            auditLog.setUserId(SecurityUtils.getCurrentUserId());
            auditLog.setUsername(SecurityUtils.getCurrentUsername());

            // 获取请求信息
            ServletRequestAttributes attrs = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                auditLog.setMethod(request.getMethod());
                auditLog.setUrl(request.getRequestURI());
                auditLog.setIp(getClientIp(request));

                // 截断请求参数
                String params = Arrays.toString(joinPoint.getArgs());
                auditLog.setParams(params.length() > 2000 ? params.substring(0, 2000) : params);
            } else {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                auditLog.setMethod("INTERNAL");
                auditLog.setUrl(signature.getDeclaringTypeName() + "." + signature.getName());
            }

            Object result = joinPoint.proceed();

            auditLog.setCostMs(Duration.between(start, Instant.now()).toMillis());
            auditLogMapper.insert(auditLog);

            return result;

        } catch (Throwable e) {
            auditLog.setStatus(0);
            auditLog.setErrorMsg(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000))
                    : e.getClass().getSimpleName());
            auditLog.setCostMs(Duration.between(start, Instant.now()).toMillis());
            auditLogMapper.insert(auditLog);
            throw e;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
