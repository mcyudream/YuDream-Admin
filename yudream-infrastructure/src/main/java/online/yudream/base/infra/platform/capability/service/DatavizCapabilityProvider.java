package online.yudream.base.infra.platform.capability.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.dataviz", name = "enabled", havingValue = "true")
public class DatavizCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "dataviz";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "数据可视化",
                CapabilityType.GRAPH,
                "ECharts / D3 数据可视化平台能力，未启用时不注册图表端点",
                "i-ri:pie-chart-line",
                90,
                Map.of(),
                List.of()
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("数据可视化未启用");
        }
        return CapabilityHealth.enabled("数据可视化能力正常", Map.of());
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("数据可视化未启用");
        }
        return CapabilityTestResult.success("数据可视化测试通过");
    }
}
