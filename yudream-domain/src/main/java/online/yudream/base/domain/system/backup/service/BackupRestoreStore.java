package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;

import java.io.InputStream;
import java.util.Collection;
import java.util.Set;

/**
 * 合并导入目标存储端口：按冲突策略把归档数据写回现网。
 * 文档写入语义：{@code replaceExisting=true} 时同 id 覆盖（upsert），否则只插入不存在的 id。
 * 对象写入语义：put 覆盖同名对象键；调用方先经 {@link #existingObjectKeys} 自行裁决。
 */
public interface BackupRestoreStore {

    /** 返回候选 id 中本地已存在的子集。 */
    Set<String> existingDocumentIds(String collection, Set<String> candidateIds);

    void writeDocuments(String collection, Collection<SnapshotDocument> documents, boolean replaceExisting);

    Set<String> existingObjectKeys(Collection<String> keys);

    void writeObject(String objectKey, InputStream in, long size);
}
