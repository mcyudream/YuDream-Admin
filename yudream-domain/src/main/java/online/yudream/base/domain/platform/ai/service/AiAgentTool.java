package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

public interface AiAgentTool {

    AiAgentToolDescriptor descriptor();

    AiAgentToolResult execute(AiAgentToolCall call);
}
