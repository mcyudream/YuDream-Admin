package online.yudream.base.domain.platform.plugin.valobj;

import java.io.IOException;
import java.io.InputStream;

/**
 * 流式 multipart part（宿主内部值对象）：由 interfaces 层基于 Servlet Part 实现
 * （容器磁盘 spool，不做整体内存缓冲），经运行时网关映射为 SPI 流式契约。
 */
public interface PluginHttpStreamingPart {

    String name();

    /** 上传文件 part 的原始文件名；字段 part 返回 null。 */
    String filename();

    /** part 的 Content-Type；可能为 null。 */
    String contentType();

    /** 该 part 的字节数（容器解析时的实际大小）。 */
    long size();

    /** 打开一个只读流读取 part 内容；基于容器 spool 可多次调用，调用方负责关闭。 */
    InputStream stream() throws IOException;

    /**
     * 分发结束后释放底层资源：关闭残余流并清理容器临时文件（Part.delete）；幂等。
     */
    void dispose();
}
