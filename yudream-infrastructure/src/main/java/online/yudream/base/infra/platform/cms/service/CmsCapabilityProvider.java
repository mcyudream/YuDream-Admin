package online.yudream.base.infra.platform.cms.service;

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
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class CmsCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "cms";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "内容定制",
                CapabilityType.CONTENT,
                "提供自定义首页和 Markdown 单页面能力",
                "i-ri:layout-masonry-line",
                50,
                Map.of("markdown", "enabled")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("内容定制能力已启用", Map.of("pageType", "markdown"))
                : CapabilityHealth.disabled("内容定制能力未启用");
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
                ? CapabilityTestResult.success("内容定制能力可用")
                : CapabilityTestResult.failure("内容定制能力未启用");
    }
}
