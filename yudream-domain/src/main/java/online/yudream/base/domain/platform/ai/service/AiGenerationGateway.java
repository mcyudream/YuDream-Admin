package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;

public interface AiGenerationGateway {

    AiGenerationResult generate(AiGenerationRequest request);
}
