package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

/** 在真实外部执行处挂起的工具。prepare 只校验并持久公布请求，不等待浏览器也不伪造成功。 */
public interface AiExternalAgentTool extends AiAgentTool {
    AiAgentToolResult prepare(String requestId, AiAgentToolCall call);

    @Override
    default AiAgentToolResult execute(AiAgentToolCall call) {
        throw new UnsupportedOperationException("外部工具必须经持久会话请求与真实执行回执完成");
    }
}
