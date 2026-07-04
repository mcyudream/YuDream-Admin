package online.yudream.base.infra.platform.ai.service.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class OpenAiCompatibleProviderAdapter extends AbstractOpenAiCompatibleProviderAdapter {

    @Override
    public AiProviderType type() {
        return AiProviderType.OPENAI_COMPATIBLE;
    }
}
