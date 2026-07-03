package online.yudream.base.infra.platform.capability.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqCapabilityConfig {

    public static final String EXCHANGE = "yudream.capability";
    public static final String QUEUE = "yudream.capability.test";
    public static final String ROUTING_KEY = "capability.test";

    @Bean
    public DirectExchange capabilityExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue capabilityTestQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding capabilityTestBinding(DirectExchange capabilityExchange, Queue capabilityTestQueue) {
        return BindingBuilder.bind(capabilityTestQueue).to(capabilityExchange).with(ROUTING_KEY);
    }
}
