package online.yudream.base.infra.system.security.bootstrap;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 权限注册启动器。
 * <p>
 * 应用启动完成后，扫描 Spring 容器中所有 Bean 的方法，收集 {@link PermissionRegister}
 * 与 {@link SaCheckPermission} 注解，并同步到数据库。
 * <p>
 * 绑定规则：
 * <ul>
 *   <li>仅标注 {@link SaCheckPermission}：自动以 value 中的权限码注册，name/module 使用默认值。</li>
 *   <li>同时标注两者：{@link PermissionRegister#code()} 为空时，自动继承 {@link SaCheckPermission} 的权限码；
 *       name/module/desc 以 {@link PermissionRegister} 为准。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRegisterBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final ApplicationContext applicationContext;
    private final PermissionDomainService permissionDomainService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Start scanning @PermissionRegister / @SaCheckPermission annotations...");
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
            ReflectionUtils.doWithMethods(targetClass, method -> collectFromMethod(method, result));
        }
        return result;
    }

    private void collectFromMethod(Method method, Set<PermissionMeta> result) {
        PermissionRegister register = AnnotatedElementUtils.findMergedAnnotation(method, PermissionRegister.class);
        SaCheckPermission saCheck = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);

        if (register == null && saCheck == null) {
            return;
        }

        String[] codes = resolveCodes(register, saCheck);
        if (codes == null || codes.length == 0) {
            return;
        }

        String name = register != null && StringUtils.hasText(register.name()) ? register.name() : null;
        String module = register != null && StringUtils.hasText(register.module()) ? register.module() : null;
        String desc = register != null ? register.desc() : "";

        for (String code : codes) {
            String finalName = name != null ? name : code;
            String finalModule = module != null ? module : "default";
            result.add(new PermissionMeta(code, finalName, finalModule, desc));
        }
    }

    private String[] resolveCodes(PermissionRegister register, SaCheckPermission saCheck) {
        if (register != null && StringUtils.hasText(register.code())) {
            return new String[]{register.code()};
        }
        if (saCheck != null && saCheck.value().length > 0) {
            return saCheck.value();
        }
        return new String[0];
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
