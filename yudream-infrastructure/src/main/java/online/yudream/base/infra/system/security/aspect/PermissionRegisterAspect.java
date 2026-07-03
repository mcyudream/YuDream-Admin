package online.yudream.base.infra.system.security.aspect;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * {@link PermissionRegister} 权限检查切面。
 * <p>
 * 方法上只标注 {@link PermissionRegister} 时，自动使用其 {@code code} 进行 Sa-Token 权限校验，
 * 效果等同于同时标注 {@link SaCheckPermission}。
 * <p>
 * 若方法已显式标注 {@link SaCheckPermission}，则交由 Sa-Token 原生切面处理，本切面跳过，避免重复校验。
 */
@Slf4j
@Aspect
@Component
public class PermissionRegisterAspect {

    @Around("@annotation(permissionRegister)")
    public Object around(ProceedingJoinPoint point, PermissionRegister permissionRegister) throws Throwable {
        String code = permissionRegister.code();
        if (!StringUtils.hasText(code)) {
            return point.proceed();
        }
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        SaCheckPermission saCheck = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);
        if (saCheck == null) {
            if (ApiKeyAuthenticationContext.hasPermission(code)) {
                log.debug("Permission check passed via API Key, code={}", code);
                return point.proceed();
            }
            StpUtil.checkPermission(code);
            log.debug("Permission check passed via @PermissionRegister, code={}", code);
        }
        return point.proceed();
    }
}
