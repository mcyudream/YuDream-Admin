package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;
import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * 备份归档读取器（随机读，基于完整文件）。清单与索引一次性可得，
 * 集合与文件按需流式读取；调用方负责关闭返回的流与读取器本身。
 */
public interface BackupArchiveReader extends Closeable {

    BackupManifest manifest();

    List<String> collections();

    /** 流式遍历集合并读出规范化 id 与 EJSON 行（id 由实现负责解析）。 */
    void streamCollection(String name, Consumer<SnapshotDocument> documentConsumer);

    List<ArchiveFileEntry> objects();

    InputStream openObject(String objectKey);

    List<BackupManifest.ManifestPluginScope> pluginScopes();

    List<ArchiveFileEntry> pluginFiles(String pluginCode, String scopeCode);

    InputStream openPluginFile(String pluginCode, String scopeCode, String path);

    /** 归档读取器工厂。 */
    interface Factory {
        BackupArchiveReader open(Path archiveFile);
    }
}
