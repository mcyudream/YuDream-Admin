package online.yudream.base.application.platform.agent.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 执行链路追踪配置。落在应用层是因为追踪器（application）与仓储实现（infra）都需要消费。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.agent.trace")
public class AgentTraceProperties {

    /** 是否记录 Agent 执行链路追踪。 */
    private boolean enabled = true;

    /** 单段文本（输入/输出/工具详情）最大长度，超出截断。 */
    private int maxTextLength = 4000;

    /** 单步思考过程最大长度，超出截断。 */
    private int maxReasoningLength = 8000;

    /** 追踪记录保留天数，由仓储实现用于 TTL 索引。 */
    private int retentionDays = 7;

    /** 每个来源最多保留的条数，超出时清理最旧记录。 */
    private int maxPerSource = 500;
}
