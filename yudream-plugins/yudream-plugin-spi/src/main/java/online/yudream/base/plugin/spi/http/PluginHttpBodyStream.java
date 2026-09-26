package online.yudream.base.plugin.spi.http;

import java.io.InputStream;

/**
 * 插件 HTTP 流式请求体：宿主在分发期间打开并交给流式端点，插件边读边处理，
 * 不会在宿主侧整体缓冲（支持大文件上传、NDJSON 等流式场景）。
 *
 * <p>流由宿主持有：返回缓冲式/SSE 响应时在处理器返回后立即关闭，返回流式响应时在响应写出
 * 结束后关闭（期间插件可继续读取以实现请求转响应回显）；插件可以提前关闭，重复关闭是安全的。
 * {@link #stream()} 只应消费一次，重复调用返回同一个流对象。</p>
 */
public interface PluginHttpBodyStream extends AutoCloseable {

    /**
     * 请求体输入流。只允许完整消费一次；读取超过宿主限制时会抛出
     * {@link PluginHttpBodySizeExceededException}（RuntimeException，宿主默认映射 413 响应）。
     */
    InputStream stream();

    /**
     * 请求头 Content-Length 声明的字节数；未声明（chunked 等）返回 -1。
     * 宿主在进入端点前已按该值做前置限长检查。
     */
    long declaredContentLength();

    /** 请求体 Content-Type；缺失时返回 null。 */
    String contentType();

    /** 关闭底层流；宿主在分发结束后保证调用一次，插件可提前调用，幂等。 */
    @Override
    void close();
}
