package online.yudream.base.application.system.backup.dto;

import java.util.List;

/** 归档合并导入分析预览：重复与缺失计数 + 兼容性告警，供管理员在执行前确认合并策略。 */
public record BackupAnalysisDTO(
        String hostVersion,
        String createdAt,
        String masterKeyFingerprint,
        boolean masterKeyMatch,
        List<CollectionAnalysis> collections,
        ObjectAnalysis objects,
        List<PluginScopeAnalysis> pluginScopes,
        List<String> warnings
) {

    public record CollectionAnalysis(String name, long archiveCount, long missingCount, long conflictCount) {
    }

    public record ObjectAnalysis(long archiveCount, long missingCount, long conflictCount, long totalBytes) {
    }

    public record PluginScopeAnalysis(String pluginCode, String scopeCode, String displayName,
                                      int fileCount, boolean available) {
    }
}
