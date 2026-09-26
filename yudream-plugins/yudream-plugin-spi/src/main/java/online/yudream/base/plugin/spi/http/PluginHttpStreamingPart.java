package online.yudream.base.plugin.spi.http;

import java.io.IOException;
import java.io.InputStream;

/**
 * multipart/form-data 请求中的一个流式 part（字段或文件）。
 * 由宿主基于 Servlet 容器的 multipart 解析（磁盘 spool）实现，插件按需打开流读取，
 * 宿主与插件侧均不做整体内存缓冲，适合大文件上传。
 *
 * <p>缓冲式读取请继续使用 {@link PluginHttpRequest#parts()}（{@link PluginHttpPart}）。</p>
 */
public interface PluginHttpStreamingPart {

    String name();

    /** 上传文件 part 的原始文件名；字段 part 返回 null。 */
    String filename();

    /** part 的 Content-Type；可能为 null。 */
    String contentType();

    /** 该 part 的字节数（容器解析时的实际大小）。 */
    long size();

    /**
     * 打开一个只读流读取 part 内容；基于容器 spool 可多次调用，
     * 每次调用返回新的流，调用方负责 close（宿主会在分发结束后兜底关闭残余流）。
     */
    InputStream stream() throws IOException;

    /** 是否为上传文件 part（带 filename）。 */
    default boolean isFile() {
        return filename() != null;
    }
}
