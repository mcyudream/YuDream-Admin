package online.yudream.base.infra.platform.capability.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class RabbitMqCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "rabbitmq";
    private static final String DEFAULT_EXCHANGE = "yudream.capability";
    private static final String DEFAULT_ROUTING_KEY = "capability.test";

    private final RabbitTemplate rabbitTemplate;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Map<String, String> config = Map.of();

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "RabbitMQ",
                CapabilityType.MESSAGING,
                "提供 AMQP 消息发送与队列连通性测试",
                "i-ri:message-3-line",
                80,
                Map.of("exchange", DEFAULT_EXCHANGE, "routingKey", DEFAULT_ROUTING_KEY)
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("RabbitMQ 未启用");
        }
        try {
            Boolean open = rabbitTemplate.execute(channel -> channel.isOpen());
            return Boolean.TRUE.equals(open)
                    ? CapabilityHealth.enabled("RabbitMQ 连接正常", Map.of("exchange", exchange(), "routingKey", routingKey()))
                    : CapabilityHealth.error("RabbitMQ 通道未打开");
        }
        catch (Exception e) {
            return CapabilityHealth.error("RabbitMQ 连接失败：" + e.getMessage());
        }
    }

    @Override
    public void enable(Map<String, String> config) {
        this.config = config == null ? Map.of() : config;
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("RabbitMQ 未启用");
        }
        try {
            rabbitTemplate.convertAndSend(exchange(), routingKey(), message);
            return CapabilityTestResult.success("RabbitMQ 测试消息已发送");
        }
        catch (Exception e) {
            return CapabilityTestResult.failure("RabbitMQ 测试发送失败：" + e.getMessage());
        }
    }

    private String exchange() {
        return config.getOrDefault("exchange", DEFAULT_EXCHANGE);
    }

    private String routingKey() {
        return config.getOrDefault("routingKey", DEFAULT_ROUTING_KEY);
    }
}
