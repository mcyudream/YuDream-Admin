package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 开发模式批量重载或批量重启的汇总结果。
 */
public record PluginDevBatchReloadResult(List<String> succeeded, List<Failed> failed) {

    public PluginDevBatchReloadResult {
        succeeded = succeeded == null ? List.of() : List.copyOf(succeeded);
        failed = failed == null ? List.of() : List.copyOf(failed);
    }

    public record Failed(String code, String reason) {
    }
}
