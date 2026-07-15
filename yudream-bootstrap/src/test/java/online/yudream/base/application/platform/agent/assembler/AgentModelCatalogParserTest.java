package online.yudream.base.application.platform.agent.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentModelCatalogParserTest {

    private final AgentModelCatalogParser parser = new AgentModelCatalogParser(new ObjectMapper());

    @Test
    void shouldExposeChatEmbeddingAndRerankModelsWithoutSecrets() {
        Map<String, String> config = Map.of("providers", """
                [{
                  "code":"main",
                  "name":"Main AI",
                  "enabled":true,
                  "apiKey":"secret-key",
                  "defaultModel":"gpt-chat",
                  "models":[{"code":"gpt-chat","name":"Chat","vision":true}],
                  "embeddingModels":["text-embedding-3-small"],
                  "rerankModels":["rerank-v3"]
                }]
                """);

        var models = parser.parse(config);

        assertThat(models).extracting(item -> item.kind() + ":" + item.modelCode())
                .containsExactly("chat:gpt-chat", "embedding:text-embedding-3-small", "rerank:rerank-v3");
        assertThat(models.getFirst().configured()).isTrue();
        assertThat(models.getFirst().vision()).isTrue();
        assertThat(models).allMatch(item -> !item.toString().contains("secret-key"));
    }

    @Test
    void shouldIgnoreDisabledProvidersAndBlankModels() {
        Map<String, String> config = Map.of("providers", """
                [
                  {"code":"off","enabled":false,"apiKey":"x","models":["hidden"]},
                  {"code":"active","enabled":true,"models":["", "chat-a"],"embeddingModels":[]}
                ]
                """);

        assertThat(parser.parse(config)).singleElement()
                .satisfies(model -> {
                    assertThat(model.providerCode()).isEqualTo("active");
                    assertThat(model.modelCode()).isEqualTo("chat-a");
                    assertThat(model.configured()).isFalse();
                });
    }

    @Test
    void shouldHonorExplicitModelKindInsideUnifiedModelsArray() {
        Map<String, String> config = Map.of("providers", """
                [{
                  "code":"main",
                  "enabled":true,
                  "apiKey":"secret-key",
                  "defaultModel":"BAAI/bge-m3",
                  "models":[
                    {"code":"BAAI/bge-m3","name":"BGE M3","kind":"embedding"},
                    {"code":"deepseek-v4-pro","name":"DeepSeek V4 Pro","kind":"chat"}
                  ]
                }]
                """);

        assertThat(parser.parse(config)).extracting(item -> item.kind() + ":" + item.modelCode())
                .containsExactly("embedding:BAAI/bge-m3", "chat:deepseek-v4-pro");
    }
}
