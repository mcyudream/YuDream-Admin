package online.yudream.base.plugin.spi.system.backup;

import java.io.InputStream;

/**
 * 备份数据接收端：宿主在导出/远程备份期间提供给插件的只写通道。
 * relativePath 为范围内部相对路径，仅允许 {@code [A-Za-z0-9._/-]}，
 * 禁止以 {@code /} 开头、包含 {@code ..} 或反斜杠，宿主会拒绝非法路径。
 *
 * @since 2.33.0
 */
public interface BackupSink {

    /** 写入一个二进制文件；宿主负责关闭传入的流并计算校验和。 */
    void putFile(String relativePath, long size, InputStream in);

    /** 写入一个 UTF-8 文本文件。 */
    void putText(String relativePath, String content);
}
