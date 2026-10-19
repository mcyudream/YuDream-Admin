package online.yudream.base.application.platform.chat.service;

import java.util.List;

/**
 * 用户工作区文件存储端口：每用户一个默认工作区，AI 助手会话与上传文件都在其中；
 * 实现侧与 Harness 远程文件系统的 KV 命名空间公式对齐（agents/general/users/&lt;uid&gt;/workspace）。
 */
public interface ChatWorkspaceStore {

    List<Entry> list(Long userId);

    byte[] readFile(Long userId, String path);

    void writeFile(Long userId, String path, byte[] data);

    void delete(Long userId, String path);

    record Entry(String path, long size, String updatedAt) {
    }
}
