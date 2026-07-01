package online.yudream.base.infra.system.security.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 权限注册启动器。
 * <p>
 * 应用启动完成后，扫描 Spring 容器中所有 Bean 的方法，收集 {@link PermissionRegister} 注解，
 * 并同步到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRegisterBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;
    private final PermissionDomainService permissionDomainService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Start scanning @PermissionRegister annotations...");
        Set<PermissionMeta> scanned = scanPermissions();
        permissionDomainService.syncPermissions(scanned);
    }

    private Set<PermissionMeta> scanPermissions() {
        Set<PermissionMeta> result = new HashSet<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                log.warn("Failed to get bean {}, skip", beanName, e);
                continue;
            }
            Class<?> targetClass = getTargetClass(bean);
            if (targetClass == null) {
                continue;
            }
            ReflectionUtils.doWithMethods(targetClass, method -> {
                PermissionRegister annotation = AnnotatedElementUtils.findMergedAnnotation(method, PermissionRegister.class);
                if (annotation != null) {
                    result.add(new PermissionMeta(annotation.code(), annotation.name(), annotation.module(), annotation.desc()));
                }
            });
        }
        return result;
    }

    private Class<?> getTargetClass(Object bean) {
        Class<?> clazz = bean.getClass();
        // 处理 Spring 代理类
        if (clazz.getName().contains("$$SpringCGLIB$$") || clazz.getName().contains("$Proxy")) {
            Class<?> targetClass = org.springframework.util.ClassUtils.getUserClass(bean);
            if (targetClass != clazz) {
                return targetClass;
            }
        }
        return clazz;
    }
}
