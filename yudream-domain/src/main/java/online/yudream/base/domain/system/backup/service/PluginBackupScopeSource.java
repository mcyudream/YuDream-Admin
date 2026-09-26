package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.valobj.ArchiveFileEntry;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 插件备份范围来源端口：聚合各已启用插件经 SPI
 * {@code PluginBackupProvider} 扩展点注册的备份范围，由基础设施实现。
 */
public interface PluginBackupScopeSource {

    /** 当前全部可用插件范围。 */
    List<PluginScopeHandle> scopes();

    Optional<PluginScopeHandle> find(String pluginCode, String scopeCode);

    /** 调用插件导出其范围数据（options 为任务触发方携带的参数，如单实例过滤）；返回告警列表。 */
    List<String> exportScope(PluginScopeHandle handle, PluginScopeSink sink, Map<String, String> options);

    /** 调用插件按策略恢复其范围数据。 */
    void restoreScope(PluginScopeHandle handle, PluginBackupFileSource files, BackupConflictStrategy strategy);

    record PluginScopeHandle(String pluginCode, String scopeCode, String displayName,
                             String description, String defaultSchedule) {
    }

    interface PluginScopeSink {
        void putFile(String relativePath, long size, InputStream in);
    }

    interface PluginBackupFileSource {
        List<ArchiveFileEntry> files();

        InputStream open(String path);
    }
}
