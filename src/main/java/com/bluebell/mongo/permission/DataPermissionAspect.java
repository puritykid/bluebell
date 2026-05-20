package com.bluebell.mongo.permission;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class DataPermissionAspect {

    private final OrganizationPermissionService permissionService;

    @Around("@annotation(com.bluebell.mongo.permission.DataPermission)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        DataPermission ann = method.getAnnotation(DataPermission.class);
        List<String> orgIds = permissionService.resolveOrganizationIds();
        DataPermissionContext.set(ann.field(), orgIds, ann.allowAllWhenEmpty());
        try {
            return pjp.proceed();
        } finally {
            DataPermissionContext.clear();
        }
    }
}
