package online.yudream.base.infra.platform.ai.service.provider;

import io.agentscope.core.model.GenerateOptions;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;

public interface AiProviderAdapter {

    AiProviderType type();

    /**
     * 组装 AgentScope 请求级生成选项（温度、toolChoice、推理力度、供应商 extraBody 等）。
     */
    GenerateOptions generateOptions(AiProviderEndpoint provider, AiModelEndpoint model, AiGenerationRequest request);

    default boolean supportsRequiredToolChoice(AiProviderEndpoint provider, AiModelEndpoint model) {
        return false;
    }
}
