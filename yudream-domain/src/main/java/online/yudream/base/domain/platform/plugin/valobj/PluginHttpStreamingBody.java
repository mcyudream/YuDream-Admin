package online.yudream.base.domain.platform.plugin.valobj;

import java.io.InputStream;

/**
 * 流式 HTTP 请求体（宿主内部值对象）：由 interfaces 层基于 Servlet 原生请求实现，
 * 经运行时网关映射为 SPI 流式契约；宿主不做整体缓冲。
 */
public interface PluginHttpStreamingBody {

    /** 请求体原生输入流；由宿主在分发结束后关闭。 */
    InputStream stream();

    /** Content-Length 声明的字节数；未声明（chunked 等）返回 -1。 */
    long declaredContentLength();

    /** 请求体 Content-Type；缺失时返回 null。 */
    String contentType();

    /** 关闭底层流；幂等。 */
    void close();
}
