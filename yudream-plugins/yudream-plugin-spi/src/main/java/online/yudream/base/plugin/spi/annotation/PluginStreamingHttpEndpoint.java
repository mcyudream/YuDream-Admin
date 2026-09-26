package online.yudream.base.plugin.spi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 流式 HTTP 端点：方法需接收 {@code PluginStreamingHttpRequest}（可再追加 PluginContext），
 * 返回 PluginHttpResponse（body 可为 PluginHttpResponseBody 实现真流式）。
 * 与 {@link PluginHttpEndpoint} 的缓冲端点相对；同一路径并存时流式优先。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginStreamingHttpEndpoint {

    String method();

    String path();

    String permission() default "";

    boolean wrapResult() default true;
}
