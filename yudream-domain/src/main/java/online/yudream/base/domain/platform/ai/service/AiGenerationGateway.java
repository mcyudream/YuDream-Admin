package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.function.Consumer;

public interface AiGenerationGateway {

    AiGenerationResult generate(AiGenerationRequest request);

    default AiGenerationResult generateStream(AiGenerationRequest request, Consumer<String> onDelta) {
        return generateStream(request, onDelta, null, null);
    }

    default AiGenerationResult generateStream(
            AiGenerationRequest request,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        throw new UnsupportedOperationException("当前 AI 网关未实现真实流式生成");
    }

    /**
     * 流式生成正文与独立 reasoning；默认委托旧签名以兼容既有网关实现。
     */
    default AiGenerationResult generateStream(
            AiGenerationRequest request,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        return generateStream(request, onDelta, onTool, onProgress);
    }
}
