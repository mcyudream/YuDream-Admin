package online.yudream.base.domain.common.cache.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 刷新缓存注解。
 * <p>
 * 方法执行成功后，使用返回值覆盖指定 Key 的缓存。
 * <p>
 * {@code key} 支持 SpEL 表达式，可引用方法参数（如 {@code #userId}）以及返回值 {@code #result}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RefreshCache {

    /**
     * 缓存 Key，支持 SpEL 表达式。
     * <p>示例：{@code "'user:info:' + #result.id"}
     */
    String key();

    /**
     * 过期时间，单位秒，默认 300 秒（5 分钟）。
     * <p>{@code -1} 表示永不过期。
     */
    long expire() default 300;

    /**
     * 刷新条件，支持 SpEL 表达式，为空或计算为 {@code true} 时才刷新缓存。
     */
    String condition() default "";

    /**
     * 是否异步刷新，默认 false。
     * <p>开启后方法立即返回，缓存写入/刷新在后台线程执行。
     */
    boolean async() default false;
}
