package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

public interface AiAgentTool {

    AiAgentToolDescriptor descriptor();

    default AgentToolRisk risk() {
        return AgentToolRisk.WRITE;
    }

    /** 只读但需确认的插件工具仍保留审批语义。 */
    default boolean requiresConfirmation() { return risk() != AgentToolRisk.READ; }

    /** 可信宿主桥接描述符；执行必须重绑定到当前插件上下文的原生注册表工具。 */
    default boolean pluginProvided() { return false; }

    AiAgentToolResult execute(AiAgentToolCall call);
}
