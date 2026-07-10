package online.yudream.base.infra.platform.render.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.domain.platform.render.service.MessageRenderGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.message-render", name = "enabled", havingValue = "true")
public class MessageRenderCapabilityProvider implements CapabilityProvider {
    public static final String CODE = "message-render";
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final MessageRenderGateway gateway;

    public MessageRenderCapabilityProvider(HttpMessageRenderGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(CODE, "消息渲染", CapabilityType.MESSAGING,
                "通过独立 Headless Chromium 服务将 HTML、Markdown 或 URL 渲染为图片", "i-ri:image-line", 74,
                Map.of("baseUrl", "http://render-server:3000", "timeout", "30s"), List.of());
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("消息渲染能力已启用", Map.of("reachable", gateway.healthy()))
                : CapabilityHealth.disabled("消息渲染能力未启用");
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
        return enabled.get() && gateway.healthy()
                ? CapabilityTestResult.success("消息渲染服务可用")
                : CapabilityTestResult.failure("消息渲染服务不可用或能力未启用");
    }
}
