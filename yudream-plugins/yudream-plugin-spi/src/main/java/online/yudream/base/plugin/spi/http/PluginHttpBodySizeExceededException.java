package online.yudream.base.plugin.spi.http;

/**
 * 插件 HTTP 流式请求体超过宿主限制时抛出：
 * 宿主在读取前按 Content-Length 前置拒绝，或在边读边计数超限时由请求体流抛出。
 * 未被插件捕获时宿主默认映射为 413（Payload Too Large）响应。
 */
public class PluginHttpBodySizeExceededException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final long limitBytes;

    public PluginHttpBodySizeExceededException(long limitBytes, String message) {
        super(message);
        this.limitBytes = limitBytes;
    }

    /** 宿主配置的流式请求体大小上限（字节）。 */
    public long limitBytes() {
        return limitBytes;
    }
}
