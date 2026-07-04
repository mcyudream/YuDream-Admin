package online.yudream.base.infra.platform.form.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.form", name = "enabled", havingValue = "true")
public class DynamicFormCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "form";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "动态表单",
                CapabilityType.CONTENT,
                "提供可视化表单设计、发布、填写、结果收集与统计能力",
                "i-ri:survey-line",
                55,
                Map.of("designer", "form-create")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("动态表单能力已启用", Map.of("designer", "form-create"))
                : CapabilityHealth.disabled("动态表单能力未启用");
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
        return enabled.get()
                ? CapabilityTestResult.success("动态表单能力可用")
                : CapabilityTestResult.failure("动态表单能力未启用");
    }
}
