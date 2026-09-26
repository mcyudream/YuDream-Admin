package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.valobj.SnapshotDocument;

import java.util.List;
import java.util.function.Consumer;

/**
 * 系统数据快照端口：枚举宿主主库业务集合并流式导出 EJSON 文档。
 * 实现方必须排除系统内部集合（system.*）与备份自身集合，保证快照不含备份元数据。
 */
public interface SystemDataSnapshotter {

    /** 全部可导出集合及估算文档数。 */
    List<CollectionSummary> collections();

    /** 最终生效的排除清单（含系统内部与备份自身集合），写入归档清单供审计。 */
    List<String> excludedCollections();

    /** 流式遍历集合内全部文档；consumer 不负责资源生命周期。 */
    void streamCollection(String name, Consumer<SnapshotDocument> consumer);

    record CollectionSummary(String name, long count) {
    }
}
