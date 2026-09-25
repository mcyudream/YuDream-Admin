package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.valobj.BackupManifest;
import online.yudream.base.domain.system.backup.valobj.BackupManifestHeader;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 备份归档写入器（顺序写）。实现负责逐集合/逐对象统计，
 * {@link #finish} 时落盘索引文件与清单并关闭底层流。
 * 对象键与插件文件路径由实现做编码与合法性校验（拒绝穿越路径）。
 */
public interface BackupArchiveWriter extends Closeable {

    void openCollection(String collection) throws IOException;

    void writeCollectionDocument(String ejson) throws IOException;

    void closeCollection() throws IOException;

    void writeObject(String objectKey, InputStream in, long size) throws IOException;

    void openPluginScope(BackupScopeRef scope) throws IOException;

    void writePluginFile(String relativePath, InputStream in, long size) throws IOException;

    void closePluginScope() throws IOException;

    /** 写入索引与清单并关闭归档。 */
    BackupManifest finish(BackupManifestHeader header) throws IOException;

    /** 归档写入目标工厂。 */
    interface Factory {
        BackupArchiveWriter create(OutputStream out);
    }
}
