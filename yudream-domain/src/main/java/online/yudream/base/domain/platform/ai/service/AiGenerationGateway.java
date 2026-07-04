package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;

import java.util.function.Consumer;

public interface AiGenerationGateway {

    AiGenerationResult generate(AiGenerationRequest request);

    default AiGenerationResult generateStream(AiGenerationRequest request, Consumer<String> onDelta) {
        AiGenerationResult result = generate(request);
        if (onDelta != null && result.summary() != null && !result.summary().isBlank()) {
            onDelta.accept(result.summary());
        }
        return result;
    }
}
