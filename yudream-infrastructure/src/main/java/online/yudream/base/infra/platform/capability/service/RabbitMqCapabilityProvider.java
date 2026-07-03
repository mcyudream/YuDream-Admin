package online.yudream.base.infra.platform.capability.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RabbitMqCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "rabbitmq";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "35672";
    private static final String DEFAULT_USERNAME = "guest";
    private static final String DEFAULT_PASSWORD = "guest";
    private static final String DEFAULT_VIRTUAL_HOST = "/";
    private static final String DEFAULT_EXCHANGE = "yudream.capability";
    private static final String DEFAULT_QUEUE = "yudream.capability.test";
    private static final String DEFAULT_ROUTING_KEY = "capability.test";

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Map<String, String> config = Map.of();
    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private RabbitAdmin rabbitAdmin;

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "RabbitMQ",
                CapabilityType.MESSAGING,
                "按需提供 AMQP 消息发送与队列连通性测试，未启用时不创建连接",
                "i-ri:message-3-line",
                80,
                Map.of(
                        "host", DEFAULT_HOST,
                        "port", DEFAULT_PORT,
                        "username", DEFAULT_USERNAME,
                        "password", DEFAULT_PASSWORD,
                        "virtualHost", DEFAULT_VIRTUAL_HOST,
                        "exchange", DEFAULT_EXCHANGE,
                        "queue", DEFAULT_QUEUE,
                        "routingKey", DEFAULT_ROUTING_KEY
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("RabbitMQ 未启用");
        }
        try {
            Boolean open = ensureTemplate().execute(channel -> channel.isOpen());
            if (!Boolean.TRUE.equals(open)) {
                return CapabilityHealth.error("RabbitMQ 通道未打开");
            }
            return CapabilityHealth.enabled("RabbitMQ 连接正常", Map.of(
                    "host", host(),
                    "port", port(),
                    "exchange", exchange(),
                    "queue", queue(),
                    "routingKey", routingKey()
            ));
        }
        catch (Exception e) {
            return CapabilityHealth.error("RabbitMQ 连接失败：" + e.getMessage());
        }
    }

    @Override
    public synchronized void enable(Map<String, String> config) {
        this.config = config == null ? Map.of() : config;
        closeConnection();
        enabled.set(true);
    }

    @Override
    public synchronized void disable() {
        enabled.set(false);
        closeConnection();
        config = Map.of();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("RabbitMQ 未启用");
        }
        try {
            declareRuntimeObjects();
            ensureTemplate().convertAndSend(exchange(), routingKey(), message);
            return CapabilityTestResult.success("RabbitMQ 测试消息已发送");
        }
        catch (Exception e) {
            return CapabilityTestResult.failure("RabbitMQ 测试发送失败：" + e.getMessage());
        }
    }

    private synchronized RabbitTemplate ensureTemplate() {
        if (rabbitTemplate != null) {
            return rabbitTemplate;
        }
        CachingConnectionFactory factory = new CachingConnectionFactory(host(), port());
        factory.setUsername(username());
        factory.setPassword(password());
        factory.setVirtualHost(virtualHost());
        connectionFactory = factory;
        rabbitTemplate = new RabbitTemplate(factory);
        rabbitAdmin = new RabbitAdmin(factory);
        return rabbitTemplate;
    }

    private void declareRuntimeObjects() {
        ensureTemplate();
        DirectExchange directExchange = new DirectExchange(exchange(), true, false);
        Queue testQueue = new Queue(queue(), true);
        Binding binding = BindingBuilder.bind(testQueue).to(directExchange).with(routingKey());
        rabbitAdmin.declareExchange(directExchange);
        rabbitAdmin.declareQueue(testQueue);
        rabbitAdmin.declareBinding(binding);
    }

    private synchronized void closeConnection() {
        rabbitTemplate = null;
        rabbitAdmin = null;
        if (connectionFactory != null) {
            connectionFactory.destroy();
            connectionFactory = null;
        }
    }

    private String host() {
        return config.getOrDefault("host", DEFAULT_HOST);
    }

    private int port() {
        try {
            return Integer.parseInt(config.getOrDefault("port", DEFAULT_PORT));
        }
        catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULT_PORT);
        }
    }

    private String username() {
        return config.getOrDefault("username", DEFAULT_USERNAME);
    }

    private String password() {
        return config.getOrDefault("password", DEFAULT_PASSWORD);
    }

    private String virtualHost() {
        return config.getOrDefault("virtualHost", DEFAULT_VIRTUAL_HOST);
    }

    private String exchange() {
        return config.getOrDefault("exchange", DEFAULT_EXCHANGE);
    }

    private String queue() {
        return config.getOrDefault("queue", DEFAULT_QUEUE);
    }

    private String routingKey() {
        return config.getOrDefault("routingKey", DEFAULT_ROUTING_KEY);
    }
}
