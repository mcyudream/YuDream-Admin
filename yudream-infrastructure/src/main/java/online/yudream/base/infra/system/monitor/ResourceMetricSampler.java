package online.yudream.base.infra.system.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.monitor.service.HostResourceAppService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceMetricSampler {

    private final HostResourceAppService hostResourceAppService;

    @Scheduled(fixedRate = 15000, initialDelay = 5000)
    public void sample() {
        try {
            hostResourceAppService.recordSample();
        } catch (Exception e) {
            log.warn("系统资源采样失败：{}", e.getMessage());
        }
    }
}
