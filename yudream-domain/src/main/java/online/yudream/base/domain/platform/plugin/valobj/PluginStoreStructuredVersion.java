package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Map;

/**
 * 结构化版本条目：LOCAL/V2_API 源的目录快照直接携带解析结果，
 * 安装与兼容性评估不再依赖 descriptor JSON 文本解析；STATIC_INDEX 源为 null 走原文路径。
 * downloadUrl 对 LOCAL 源为 {@code local:{jarPath}} 标记（进程内文件拷贝），对 V2 源为绝对下载地址。
 * gitUrl 来自 plugin.yml 可选 git 声明（发布物 sourceUrl）。
 */
public record PluginStoreStructuredVersion(
        String releaseVersion,
        String downloadUrl,
        String sha256,
        String main,
        String displayName,
        String description,
        Long sizeBytes,
        String category,
        List<String> tags,
        Map<String, String> compatibility,
        List<PluginStorePluginDependency> dependencies,
        String gitUrl) {

    public PluginStoreStructuredVersion {
        tags = tags == null ? List.of() : List.copyOf(tags);
        gitUrl = gitUrl == null || gitUrl.isBlank() ? null : gitUrl.trim();
        compatibility = compatibility == null ? Map.of() : Map.copyOf(compatibility);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
