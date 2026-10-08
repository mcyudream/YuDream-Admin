package online.yudream.base.infra.platform.ai.service.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.util.StringUtils;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 联调复现：智谱 provider 在 resolve 链上的选择与模型解析。 */
// 联调回归：智谱 provider 在 resolve 链的选择与模型解析（key 为测试占位）。
class ZhipuResolveProbeTest {

    private static final String PROVIDERS = """
            [
             {"code":"openai","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.openai.com/v1","models":["gpt-4o-mini"]},
             {"code":"kimi","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.moonshot.cn/v1","models":["kimi"]},
             {"code":"deepseek","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.deepseek.com","models":["deepseek"]},
             {"code":"zhipu-coding","name":"智谱 Coding Plan","type":"OPENAI_COMPATIBLE",
              "baseUrl":"https://open.bigmodel.cn/api/coding/paas/v4",
              "apiKey":"test-key-not-real","enabled":true,
              "defaultModel":"glm-4.6","models":["glm-4.6"]}
            ]
            """;

    @Test
    void resolvesZhipuProviderAndModel() {
        var resolved = new AiProviderConfigParser().resolve(
                Map.of("providers", PROVIDERS),
                "zhipu-coding",
                "glm-4.6",
                List.of(new OpenAiCompatibleProviderAdapter())
        );
        assertEquals("zhipu-coding", resolved.provider().code());
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", resolved.provider().endpointBaseUrl());
        assertEquals("glm-4.6", resolved.model().modelName());
        assertTrue(resolved.provider().apiKey() != null && !resolved.provider().apiKey().isBlank(), "apiKey");
    }
}
