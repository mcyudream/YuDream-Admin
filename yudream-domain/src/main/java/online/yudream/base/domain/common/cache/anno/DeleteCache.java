package online.yudream.base.domain.common.cache.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 删除缓存注解。
 * <p>
 * 方法执行成功后，删除指定 Key 的缓存。Key 中包含 {@code *} / {@code ?} 等通配符时，
 * 将使用 SCAN 批量删除所有匹配 Key。
 * <p>
 * {@code key} 支持 SpEL 表达式，可引用方法参数（如 {@code #userId}）以及返回值 {@code #result}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeleteCache {

    /**
     * 缓存 Key，支持 SpEL 表达式。
     * <p>示例：{@code "'user:info:' + #userId"} 或 {@code "'user:info:*'"}
     */
    String key();

    /**
     * 删除条件，支持 SpEL 表达式，为空或计算为 {@code true} 时才删除缓存。
     */
    String condition() default "";
}
