package online.yudream.base.infra.platform.ai.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.ai.client")
public class AiClientProperties {

    private Duration connectTimeout = Duration.ofSeconds(30);

    private Duration readTimeout = Duration.ofMinutes(10);

    private Duration sseTimeout = Duration.ofMinutes(10);

    /**
     * 注入模型的历史对话最大轮数（1 轮 = 1 条 user + 1 条 assistant）。
     * 超出的更早历史会被裁剪，避免 token 无限增长。
     */
    private int historyMaxTurns = 8;
}
