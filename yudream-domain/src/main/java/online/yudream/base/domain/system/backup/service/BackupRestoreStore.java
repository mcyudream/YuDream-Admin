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

    /** 返回 keyValues 中本地已存在的业务键值子集（逻辑唯一键判重，如权限/菜单的 code）。 */
    Set<String> existingBusinessKeyValues(String collection, String keyField, Collection<String> keyValues);

    /** 删除本地同业务键的全部文档（ARCHIVE_WINS 覆盖前清理，随后写入归档版本）。返回删除数。 */
    long purgeByBusinessKeys(String collection, String keyField, Collection<String> keyValues);

    Set<String> existingObjectKeys(Collection<String> keys);

    void writeObject(String objectKey, InputStream in, long size);
}
