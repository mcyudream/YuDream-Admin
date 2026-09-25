package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 归档分析预览响应。 */
@Data
@Builder
public class BackupAnalysisRes {
    private String hostVersion;
    private String createdAt;
    private String masterKeyFingerprint;
    private Boolean masterKeyMatch;
    private List<CollectionAnalysis> collections;
    private ObjectAnalysis objects;
    private List<PluginScopeAnalysis> pluginScopes;
    private List<String> warnings;

    @Data
    @Builder
    public static class CollectionAnalysis {
        private String name;
        private Long archiveCount;
        private Long missingCount;
        private Long conflictCount;
    }

    @Data
    @Builder
    public static class ObjectAnalysis {
        private Long archiveCount;
        private Long missingCount;
        private Long conflictCount;
        private Long totalBytes;
    }

    @Data
    @Builder
    public static class PluginScopeAnalysis {
        private String pluginCode;
        private String scopeCode;
        private String displayName;
        private Integer fileCount;
        private Boolean available;
    }
}
