package com.bluebell.mongo.permission;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 从方法参数解析业务机构 ID。
 */
public final class DataPermissionParamResolver {

    private DataPermissionParamResolver() {
    }

    public static String resolveBizOrgId(ProceedingJoinPoint pjp, DataPermission ann) {
        // 1. 优先：标注 @BizOrgId 的参数
        String fromAnnotation = resolveByBizOrgIdAnnotation(pjp);
        if (fromAnnotation != null) {
            return fromAnnotation;
        }
        // 2. 按参数名（需编译 -parameters）
        if (ann.bizOrgParam() != null && !ann.bizOrgParam().isBlank()) {
            return resolveByParamName(pjp, ann.bizOrgParam());
        }
        return null;
    }

    private static String resolveByBizOrgIdAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(BizOrgId.class) && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }

    private static String resolveByParamName(ProceedingJoinPoint pjp, String paramName) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = pjp.getArgs();
        if (names == null) {
            return null;
        }
        for (int i = 0; i < names.length; i++) {
            if (paramName.equals(names[i]) && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }
}
