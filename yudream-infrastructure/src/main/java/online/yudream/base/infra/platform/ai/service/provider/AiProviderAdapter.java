package online.yudream.base.infra.platform.ai.service.provider;

import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import org.springframework.ai.openai.OpenAiChatOptions;

public interface AiProviderAdapter {

    AiProviderType type();

    OpenAiChatOptions chatOptions(AiProviderEndpoint provider, AiModelEndpoint model, AiGenerationRequest request);
}
