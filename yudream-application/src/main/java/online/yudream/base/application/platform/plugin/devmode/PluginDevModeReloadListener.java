package online.yudream.base.application.platform.plugin.devmode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 消费开发模式重载请求，走完整重载管线（回收 → 目录加载 → 恢复启用 → 权限/菜单同步），
 * 并把重载结果作为 RELOAD 生命周期事件广播给开发者工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginDevModeReloadListener {

    private final PluginAppService pluginAppService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onReloadRequested(PluginDevReloadRequested request) {
        long startNanos = System.nanoTime();
        try {
            pluginAppService.reloadDevPlugin(request.pluginCode());
            eventPublisher.publishEvent(PluginLifecycleEvent.succeeded(
                    request.pluginCode(), PluginLifecycleAction.RELOAD, null, elapsedMs(startNanos)));
        } catch (Exception e) {
            log.warn("Dev-mode plugin reload failed: {}", request.pluginCode(), e);
            eventPublisher.publishEvent(PluginLifecycleEvent.failed(
                    request.pluginCode(), PluginLifecycleAction.RELOAD, null, elapsedMs(startNanos), rootMessage(e)));
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
