package online.yudream.base.domain.system.security.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注册注解。
 * <p>
 * 标注在方法上，应用启动时会自动扫描并将权限信息同步到数据库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionRegister {
    /** 唯一编码，如 "user:create" */
    String code();
    /** 展示名称 */
    String name();
    /** 所属模块 */
    String module();
    /** 描述 */
    String desc() default "";
}
