package online.yudream.base.application.platform.plugin.devmode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssetsDiff;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 消费开发模式重载请求，走完整重载管线（回收 → 目录加载 → 恢复启用 → 权限/菜单同步），
 * 并把重载结果作为 RELOAD 生命周期事件广播给开发者工具；成功时附带重载前后的运行时资产差异。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginDevModeReloadListener {

    private final PluginAppService pluginAppService;
    private final PluginRuntimeGateway runtimeGateway;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onReloadRequested(PluginDevReloadRequested request) {
        long startNanos = System.nanoTime();
        PluginRuntimeAssets before = snapshot(request.pluginCode());
        try {
            pluginAppService.reloadDevPlugin(request.pluginCode());
            eventPublisher.publishEvent(PluginLifecycleEvent.succeeded(
                    request.pluginCode(), PluginLifecycleAction.RELOAD, null, elapsedMs(startNanos),
                    PluginRuntimeAssetsDiff.diff(before, snapshot(request.pluginCode()))));
        } catch (Exception e) {
            log.warn("Dev-mode plugin reload failed: {}", request.pluginCode(), e);
            eventPublisher.publishEvent(PluginLifecycleEvent.failed(
                    request.pluginCode(), PluginLifecycleAction.RELOAD, null, elapsedMs(startNanos), rootMessage(e)));
        }
    }

    private PluginRuntimeAssets snapshot(String pluginCode) {
        try {
            return runtimeGateway.runtimeAssets(pluginCode);
        } catch (RuntimeException e) {
            // 快照失败不阻断重载，差异退化为空清单
            return PluginRuntimeAssets.unloaded(pluginCode);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "开发模式重载失败" : cursor.getMessage();
    }
}
