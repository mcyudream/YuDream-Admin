package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 异地备份存储端口：一台 FTP/FTPS/WebDAV 远端的文件操作。
 * path 为目标基础路径下的相对路径，实现负责与远端目录结构映射；
 * 连接均为短生命周期（每次操作建连、用毕断开），不持长驻资源。
 */
public interface RemoteBackupStorage {

    /** 探测连接与凭据；失败抛出业务异常。 */
    void test();

    /** 上传文件（覆盖同名）。 */
    void put(String path, InputStream in, long size);

    /** 列出前缀下的文件（不含子目录递归）。 */
    List<RemoteEntry> list(String prefix);

    /** 下载远端文件到本地目标文件（覆盖）。 */
    void fetch(String path, Path destination);

    /** 删除远端文件。 */
    void delete(String path);

    /** 按目标聚合构建客户端。 */
    interface Factory {
        RemoteBackupStorage create(RemoteTarget target);
    }
}
