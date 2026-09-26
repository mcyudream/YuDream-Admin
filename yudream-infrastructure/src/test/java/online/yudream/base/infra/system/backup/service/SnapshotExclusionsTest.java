package online.yudream.base.infra.system.backup.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotExclusionsTest {

    @Test
    void defaultsExcludeTelemetryAndEphemeralTokens() {
        SnapshotExclusions exclusions = new SnapshotExclusions(null);
        assertTrue(exclusions.excluded("sysApiLog"));
        assertTrue(exclusions.excluded("sysLoginLog"));
        assertTrue(exclusions.excluded("sysResourceMetric"));
        assertTrue(exclusions.excluded("platformAgentExecutionTrace"));
        assertTrue(exclusions.excluded("oauthAccessToken"));
        assertTrue(exclusions.excluded("sysRefreshTokenCredential"));
    }

    @Test
    void coreSysCollectionsStayIncluded() {
        SnapshotExclusions exclusions = new SnapshotExclusions(null);
        assertFalse(exclusions.excluded("sysUser"));
        assertFalse(exclusions.excluded("sysRole"));
        assertFalse(exclusions.excluded("sysMenu"));
        assertFalse(exclusions.excluded("sysSetting"));
        assertFalse(exclusions.excluded("sysFileObject"));
        assertFalse(exclusions.excluded("sysApiKeyCredential"));
        assertFalse(exclusions.excluded("platformPlugin"));
    }

    @Test
    void alwaysExcludedBackupOwnAndSystemNamespace() {
        SnapshotExclusions exclusions = new SnapshotExclusions("");
        assertTrue(exclusions.excluded("sysBackupJob"));
        assertTrue(exclusions.excluded("sysBackupTarget"));
        assertTrue(exclusions.excluded("sysBackupPlan"));
        assertTrue(exclusions.excluded("system.views"));
        // 配置整组覆盖也不影响硬排除
        SnapshotExclusions overridden = new SnapshotExclusions("plugin_foo__metrics");
        assertTrue(overridden.excluded("sysBackupJob"));
        assertTrue(overridden.excluded("system.views"));
    }

    @Test
    void configReplacesDefaultsAndSupportsWildcard() {
        SnapshotExclusions exclusions = new SnapshotExclusions("plugin_mcpanel__mcpanel_metrics*, my_log");
        assertFalse(exclusions.excluded("sysApiLog"));
        assertTrue(exclusions.excluded("plugin_mcpanel__mcpanel_metrics"));
        assertTrue(exclusions.excluded("plugin_mcpanel__mcpanel_metrics_20260101"));
        assertTrue(exclusions.excluded("my_log"));
        assertFalse(exclusions.excluded("plugin_mcpanel__mcpanel_node_metrics"));
        assertFalse(exclusions.excluded("sysUser"));
    }

    @Test
    void describeReportsEffectiveRules() {
        assertEquals(List.of("sysBackupJob", "sysBackupTarget", "sysBackupPlan", "system.*",
                "sysApiLog", "oauthAccessToken"), new SnapshotExclusions(null).describe()
                .stream().filter(name -> List.of("sysBackupJob", "sysBackupTarget", "sysBackupPlan",
                        "system.*", "sysApiLog", "oauthAccessToken").contains(name)).toList());
        assertTrue(new SnapshotExclusions("plugin_x__m*").describe().contains("plugin_x__m*"));
    }
}
