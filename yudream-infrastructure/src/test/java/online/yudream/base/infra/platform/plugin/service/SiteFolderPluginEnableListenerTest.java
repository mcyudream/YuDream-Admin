package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件启用时幂等创建站点目录；失败事件与停用动作不创建。
 */
class SiteFolderPluginEnableListenerTest {

    @TempDir
    Path sitesRoot;

    private SiteFolderPluginFileStore store() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.<String, Object>of(
                SiteFolderPluginFileStore.ROOT_PROPERTY, sitesRoot.toString(),
                SiteFolderPluginFileStore.SITE_CODE_PROPERTY, "default")));
        return new SiteFolderPluginFileStore(environment, () -> false);
    }

    @Test
    void enableSuccessCreatesPluginSiteDirectory() {
        SiteFolderPluginFileStore store = store();
        new SiteFolderPluginEnableListener(store)
                .onPluginLifecycle(PluginLifecycleEvent.succeeded("alpha", PluginLifecycleAction.ENABLE, "1.0.0", 5L));

        assertTrue(Files.isDirectory(sitesRoot.resolve("default/plugins/alpha")));
    }

    @Test
    void failedEnableAndOtherActionsDoNotCreateDirectories() {
        SiteFolderPluginFileStore store = store();
        SiteFolderPluginEnableListener listener = new SiteFolderPluginEnableListener(store);

        listener.onPluginLifecycle(PluginLifecycleEvent.failed("failed", PluginLifecycleAction.ENABLE, "1.0.0", 5L, "boom"));
        listener.onPluginLifecycle(PluginLifecycleEvent.succeeded("disabled", PluginLifecycleAction.DISABLE, "1.0.0", 5L));
        listener.onPluginLifecycle(null);

        assertFalse(Files.exists(sitesRoot.resolve("default/plugins/failed")));
        assertFalse(Files.exists(sitesRoot.resolve("default/plugins/disabled")));
    }

    @Test
    void illegalPluginCodeIsWarnedNotThrown() {
        SiteFolderPluginFileStore store = store();
        new SiteFolderPluginEnableListener(store)
                .onPluginLifecycle(PluginLifecycleEvent.succeeded("../evil", PluginLifecycleAction.ENABLE, "1.0.0", 1L));

        assertFalse(Files.exists(sitesRoot.resolve("default/plugins")));
    }
}
