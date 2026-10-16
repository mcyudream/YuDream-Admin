package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 插件启用时幂等创建站点目录 {@code sites/{siteCode}/plugins/{pluginCode}/}。
 * <p>
 * 创建失败只告警不阻断启用：目录在插件首次经 {@code context.siteFiles()} 访问时还会再确保一次，
 * 不因文件系统瞬时问题让整个插件启用失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteFolderPluginEnableListener {

    private final SiteFolderPluginFileStore siteFolderStore;

    @EventListener
    public void onPluginLifecycle(PluginLifecycleEvent event) {
        if (event == null || !event.success() || event.action() != PluginLifecycleAction.ENABLE
                || event.pluginCode() == null || event.pluginCode().isBlank()) {
            return;
        }
        try {
            String directory = siteFolderStore.ensurePluginDirectory(event.pluginCode());
            log.debug("插件站点目录就绪: code={}, directory={}", event.pluginCode(), directory);
        }
        catch (RuntimeException exception) {
            log.warn("插件站点目录创建失败（不阻断启用）: code={}, error={}",
                    event.pluginCode(), exception.getMessage());
        }
    }
}
