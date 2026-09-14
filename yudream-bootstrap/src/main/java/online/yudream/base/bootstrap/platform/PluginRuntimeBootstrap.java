package online.yudream.base.bootstrap.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.milky.service.OfficialQqBotCommandMenuAppService;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 插件必须在 Tomcat 对外接流量之前恢复完毕。
 * {@link ApplicationReadyEvent} 发生在 WebServerStartStopLifecycle 之后，
 * 启动器这类匿名探测（GET /api/plugins/{code}/v1/manifest）会打到「插件未启用」。
 * SmartLifecycle.phase 低于 WebServerStartStopLifecycle（Integer.MAX_VALUE - 1024），
 * 保证 restore 先于端口绑定；官方 QQ 菜单同步仍等 ready，避免拖慢端口开放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginRuntimeBootstrap implements SmartLifecycle, ApplicationListener<ApplicationReadyEvent> {

    static final int PHASE = Integer.MAX_VALUE - 2048;

    private final PluginAppService pluginAppService;
    private final OfficialQqBotCommandMenuAppService officialCommandMenuAppService;
    private volatile boolean running;

    @Override
    public void start() {
        if (running) {
            return;
        }
        officialCommandMenuAppService.runWithoutLifecycleSync(pluginAppService::restoreEnabledPlugins);
        log.info("Platform plugins restored.");
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        officialCommandMenuAppService.prefetchRemoteSnapshots();
        officialCommandMenuAppService.syncEnabledOfficialConnections();
    }
}
