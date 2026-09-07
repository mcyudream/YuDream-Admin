package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 从父目录批量登记开发模式项目的结果：新登记项与按编码/路径去重后跳过的条目。
 */
public record PluginDevProjectScanResult(
        List<PluginDevProjectInfo> registered,
        List<Skipped> skipped
) {

    public PluginDevProjectScanResult {
        registered = registered == null ? List.of() : List.copyOf(registered);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    /**
     * 扫描中跳过的目录。code 在无法推断编码时为空。
     */
    public record Skipped(String code, String path, String reason) {
    }
}
