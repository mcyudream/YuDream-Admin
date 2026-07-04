package online.yudream.base.bootstrap.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PluginRuntimeBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final PluginAppService pluginAppService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        pluginAppService.restoreEnabledPlugins();
        log.info("Platform plugins restored.");
    }
}
