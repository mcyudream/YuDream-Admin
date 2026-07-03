package online.yudream.base.bootstrap.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityRuntimeBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private final CapabilityAppService capabilityAppService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        capabilityAppService.restoreEnabledProviders();
        log.info("Platform capability providers restored.");
    }
}
