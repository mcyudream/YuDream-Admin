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
        AiGenerationResult result = generate(request);
        if (onDelta != null && result.summary() != null && !result.summary().isBlank()) {
            onDelta.accept(result.summary());
        }
        if (onTool != null && result.toolResults() != null) {
            result.toolResults().forEach(onTool);
        }
        return result;
    }
}
