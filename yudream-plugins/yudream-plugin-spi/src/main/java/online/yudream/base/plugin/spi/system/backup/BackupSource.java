package online.yudream.base.plugin.spi.system.backup;

import java.io.InputStream;
import java.util.List;

/**
 * 备份数据来源端：宿主在合并导入期间提供给插件的只读通道，
 * 只包含该插件自己范围导出的文件；调用 {@link #open} 后负责关闭返回的流。
 *
 * @since 2.33.0
 */
public interface BackupSource {

    /** 本范围的全部文件清单。 */
    List<ScopedBackupFile> files();

    /** 打开其中一个文件；路径必须是 {@link #files()} 返回的 path。 */
    InputStream open(String relativePath);
}
