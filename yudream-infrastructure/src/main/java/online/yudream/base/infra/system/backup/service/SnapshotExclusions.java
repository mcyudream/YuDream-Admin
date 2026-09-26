package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 集合排除策略：始终排除备份自身集合（恢复会复活旧任务/覆盖新目标，自引用污染）
 * 与 Mongo 内部命名空间（system.*）；日志/遥测/短时效令牌类集合默认排除、可整组覆盖。
 * 条目支持「名称精确匹配」与「前缀*」通配（如 plugin_mcpanel__mcpanel_metrics*）。
 */
public final class SnapshotExclusions {

    /** 默认排除：高频日志/遥测、运行时队列/缓存、短时效凭据（恢复后需重新登录，避免复活会话）。 */
    public static final List<String> DEFAULT_EXCLUDED = List.of(
            // 高频日志与遥测：体积大、可随时清空、无迁移价值
            "sysApiLog",
            "sysLoginLog",
            "sysResourceMetric",
            "platformAgentExecutionTrace",
            "platformRuntimeExecutionLog",
            "platformHttpInvocationLog",
            "platformGraphQueryLog",
            // 运行时队列与可重建缓存
            "platformWikiIngestTask",
            "platformPluginMarketSourceSnapshot",
            // 短时效凭据：备份不携带活体令牌，防止恢复后复活会话
            "oauthAuthorizationCode",
            "oauthAccessToken",
            "sysRefreshTokenCredential");

    /** 硬排除：备份自身三表 + Mongo 内部命名空间；不参与配置覆盖。 */
    public static final List<String> ALWAYS_EXCLUDED = List.of(
            "sysBackupJob", "sysBackupTarget", "sysBackupPlan", "system.*");

    private static final String WILDCARD_SUFFIX = "*";

    private final Set<String> exactNames = new LinkedHashSet<>();
    private final Set<String> prefixes = new LinkedHashSet<>();

    public SnapshotExclusions(String configValue) {
        List<String> entries = configValue == null || configValue.isBlank()
                ? DEFAULT_EXCLUDED
                : parseConfig(configValue);
        // 硬排除始终合并：备份自身集合与 system.* 不受配置覆盖影响
        for (String entry : ALWAYS_EXCLUDED) {
            if (entry.endsWith(WILDCARD_SUFFIX)) {
                prefixes.add(entry.substring(0, entry.length() - WILDCARD_SUFFIX.length()));
            } else {
                exactNames.add(entry);
            }
        }
        for (String entry : entries) {
            if (entry.endsWith(WILDCARD_SUFFIX)) {
                String prefix = entry.substring(0, entry.length() - WILDCARD_SUFFIX.length());
                if (prefix.isBlank()) {
                    throw new BizException("排除规则不能只有通配符：" + entry);
                }
                prefixes.add(prefix);
            } else {
                exactNames.add(entry);
            }
        }
    }

    private static List<String> parseConfig(String configValue) {
        List<String> entries = new ArrayList<>();
        for (String part : configValue.split("[,;\\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        if (entries.isEmpty()) {
            return DEFAULT_EXCLUDED;
        }
        return entries;
    }

    /** 集合是否被排除。 */
    public boolean excluded(String collection) {
        if (collection.startsWith("system.")) {
            return true;
        }
        return exactNames.contains(collection) || prefixes.stream().anyMatch(collection::startsWith);
    }

    /** 最终生效的排除规则清单（写入归档 manifest 供审计）。 */
    public List<String> describe() {
        Set<String> rules = new LinkedHashSet<>(ALWAYS_EXCLUDED);
        rules.addAll(exactNames);
        for (String prefix : prefixes) {
            rules.add(prefix + WILDCARD_SUFFIX);
        }
        return List.copyOf(rules);
    }
}
