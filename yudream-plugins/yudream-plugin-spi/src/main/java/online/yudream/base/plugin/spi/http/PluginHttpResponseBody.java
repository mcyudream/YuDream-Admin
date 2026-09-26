package online.yudream.base.plugin.spi.http;

import java.io.InputStream;

/**
 * 插件 HTTP 流式响应体：作为 {@link PluginHttpResponse} 的 body 返回，
 * 宿主识别后直接把流写入 Servlet 原生输出（不经整体缓冲，支持大文件下载等场景）。
 *
 * <p>流由插件打开、宿主负责在写出完成后关闭（含客户端中断场景）；
 * 流式响应视为 raw 输出，{@code wrapped} 标记被忽略，且不会做 JSON 包装或接口加密（与 SSE 同语义）。
 * 请显式设置 {@link PluginHttpResponse} 的 contentType（如 application/octet-stream），
 * contentLength 大于等于 0 时宿主会下发 Content-Length 头，便于客户端展示进度。</p>
 */
public interface PluginHttpResponseBody extends AutoCloseable {

    /** 响应内容输入流；宿主写出时消费一次。 */
    InputStream stream();

    /**
     * 预期写入字节数；未知（动态生成）返回 -1，宿主按 chunked/连接关闭语义输出。
     */
    long contentLength();

    /** 关闭底层资源；宿主在写出结束后保证调用一次，幂等。 */
    @Override
    void close();
}
