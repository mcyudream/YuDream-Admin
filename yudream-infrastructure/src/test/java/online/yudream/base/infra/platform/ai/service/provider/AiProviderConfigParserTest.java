package online.yudream.base.infra.platform.ai.service.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiProviderConfigParserTest {

    @Test
    void resolvesSelectedProviderAndModelFromProvidersConfig() {
        String providers = """
                [
                  {
                    "code": "yudream-chat",
                    "name": "YuDream Chat",
                    "type": "OPENAI_COMPATIBLE",
                    "baseUrl": "https://example.com/v1",
                    "apiKey": "configured-key",
                    "defaultModel": "deepseek-v4-pro",
                    "models": [
                      { "code": "deepseek-v4-pro", "name": "DeepSeek V4 Pro", "model": "deepseek-v4-pro" }
                    ]
                  }
                ]
                """;

        var resolved = new AiProviderConfigParser().resolve(
                Map.of("providers", providers),
                "yudream-chat",
                "deepseek-v4-pro",
                List.of(new OpenAiCompatibleProviderAdapter())
        );

        assertEquals("yudream-chat", resolved.provider().code());
        assertEquals("configured-key", resolved.provider().apiKey());
        assertEquals("deepseek-v4-pro", resolved.model().modelName());
    }
}
