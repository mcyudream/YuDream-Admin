package online.yudream.base.application.platform.cms.service;

import java.util.List;

/**
 * CMS 静态站点工作区文件存储端口：与 CMS builder agent 的 Harness 工作区共享同一
 * KV 命名空间（实现侧负责与框架路由公式对齐）；发布 = 工作区到发布命名空间的整树快照。
 */
public interface CmsWorkspaceStore {

    void delete(Long userId, String path);

    byte[] readFile(Long userId, String path);

    void writeFile(Long userId, String path, byte[] data);

    List<WorkspaceEntry> list(Long userId);

    int publish(Long userId);

    byte[] readPublished(Long userId, String path);

    /** 公开端读取站点级发布快照（匿名，最后发布者胜）。 */
    byte[] readPublishedSite(String path);

    record WorkspaceEntry(String path, long size, String updatedAt) {
    }
}
