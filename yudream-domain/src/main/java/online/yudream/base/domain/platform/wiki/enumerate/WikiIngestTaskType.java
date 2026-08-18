package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 持久化摄入队列的任务类型。
 */
public enum WikiIngestTaskType {
    /** 摄入一个原始资料并生成/更新 Wiki 页面。 */
    INGEST,
    /** 删除原始资料后的级联清理。 */
    DELETE_CLEANUP,
    /** 深度研究：网络搜索并合成研究页面。 */
    DEEP_RESEARCH,
    /** 重建索引：从现有页面重建 index.md / overview.md 或向量索引。 */
    REINDEX,
    /** 重新扫描配置的 source 文件夹。 */
    RESCAN
}
