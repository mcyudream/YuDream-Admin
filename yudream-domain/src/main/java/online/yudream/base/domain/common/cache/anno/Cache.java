package online.yudream.base.domain.common.cache.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级缓存注解。
 * <p>
 * 执行前优先从 Redis 读取缓存；命中则直接返回缓存值，未命中则执行方法并将返回值写入缓存。
 * <p>
 * {@code key} 支持 SpEL 表达式，可引用方法参数（如 {@code #userId}）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cache {

    /**
     * 缓存 Key，支持 SpEL 表达式。
     * <p>示例：{@code "'user:info:' + #userId"}
     */
    String key();

    /**
     * 过期时间，单位秒，默认 300 秒（5 分钟）。
     * <p>{@code -1} 表示永不过期。
     */
    long expire() default 300;

    /**
     * 缓存条件，支持 SpEL 表达式，为空或计算为 {@code true} 时才进行缓存。
     */
    String condition() default "";

    /**
     * 是否允许缓存 null 值，默认 false。
     */
    boolean cacheNull() default false;

    /**
     * null 值缓存过期时间，单位秒，默认 60 秒。
     * <p>仅在 {@link #cacheNull()} 为 true 时生效。
     */
    long nullExpire() default 60;

    /**
     * 布隆过滤器名称，为空则不启用。
     * <p>启用后，缓存未命中时会先检查布隆过滤器；若判定一定不存在，直接返回 null。
     */
    String bloomFilter() default "";
}
