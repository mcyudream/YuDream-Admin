package online.yudream.base.infra.platform.ai.service.provider;

import online.yudream.base.domain.platform.ai.enumerate.AiEndpointUsage;
import online.yudream.base.domain.platform.ai.enumerate.AiProviderProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void ignoresEmbeddingDefaultWhenResolvingChatGenerationModel() {
        String providers = """
                [{
                  "code":"yudream",
                  "name":"YuDream",
                  "type":"OPENAI_COMPATIBLE",
                  "baseUrl":"https://example.com/v1",
                  "apiKey":"configured-key",
                  "defaultModel":"BAAI/bge-m3",
                  "models":[
                    {"code":"BAAI/bge-m3","model":"BAAI/bge-m3","kind":"embedding"},
                    {"code":"deepseek-v4-pro","model":"deepseek-v4-pro","kind":"chat"}
                  ]
                }]
                """;

        var resolved = new AiProviderConfigParser().resolve(
                Map.of("providers", providers),
                "yudream",
                null,
                List.of(new OpenAiCompatibleProviderAdapter())
        );

        assertEquals("deepseek-v4-pro", resolved.model().modelName());
    }
    @Test
    void parsesUsageAndProtocolForIndependentEndpoints() {
        String providers = """
                [
                  {
                    "code": "claude",
                    "name": "Claude",
                    "usage": "chat",
                    "protocol": "ANTH",
                    "baseUrl": "https://api.anthropic.com/v1/messages",
                    "apiKey": "anthropic-key",
                    "models": [{ "code": "claude-sonnet", "model": "claude-sonnet" }]
                  },
                  {
                    "code": "bge",
                    "name": "本地向量化",
                    "usage": "embedding",
                    "protocol": "EMBEDDING",
                    "baseUrl": "http://127.0.0.1:11434/v1",
                    "apiKey": "local-key",
                    "models": [{ "code": "bge-m3", "model": "bge-m3" }]
                  },
                  {
                    "code": "jina",
                    "name": "重排",
                    "usage": "rerank",
                    "baseUrl": "https://api.jina.ai/v1",
                    "apiKey": "jina-key",
                    "models": [{ "code": "jina-reranker-v2", "model": "jina-reranker-v2" }]
                  }
                ]
                """;
        var parser = new AiProviderConfigParser();
        Map<String, String> config = Map.of("providers", providers);

        var chat = parser.endpoints(config, AiEndpointUsage.CHAT);
        assertEquals(1, chat.size());
        assertEquals(AiProviderProtocol.ANTH, chat.get(0).protocol());
        // 粘贴完整端点地址时，端点基址应剥离协议路径交给 SDK 自行拼接。
        assertEquals("https://api.anthropic.com", chat.get(0).endpointBaseUrl());
        assertEquals(List.of("claude"), chat.stream().map(item -> item.code()).toList());

        var embedding = parser.find(config, AiEndpointUsage.EMBEDDING, "bge").orElseThrow();
        assertEquals(List.of("bge-m3"), embedding.embeddingModelNames());
        assertEquals("http://127.0.0.1:11434/v1/embeddings", embedding.endpointUrl());

        var rerank = parser.find(config, AiEndpointUsage.RERANK, "jina").orElseThrow();
        assertEquals(AiProviderProtocol.RERANK, rerank.protocol());
        assertEquals("https://api.jina.ai/v1/rerank", rerank.endpointUrl());
    }

    @Test
    void synthesizesIndependentEndpointsFromLegacyModelLists() {
        String providers = """
                [{
                  "code": "openai",
                  "name": "OpenAI",
                  "type": "OPENAI",
                  "baseUrl": "https://api.openai.com/v1",
                  "apiKey": "sk-legacy",
                  "defaultModel": "gpt-4o-mini",
                  "models": [{ "code": "gpt-4o-mini", "model": "gpt-4o-mini" }],
                  "embeddingModels": ["text-embedding-3-small"],
                  "rerankModels": ["bge-reranker-v2-m3"]
                }]
                """;
        var parser = new AiProviderConfigParser();
        Map<String, String> config = Map.of("providers", providers);

        // 旧配置的聊天端点仍然可用，向量化/重排被合成为同编码的独立端点。
        assertEquals(1, parser.endpoints(config, AiEndpointUsage.CHAT).size());
        var embedding = parser.find(config, AiEndpointUsage.EMBEDDING, "openai").orElseThrow();
        assertEquals("sk-legacy", embedding.apiKey());
        assertEquals(AiProviderProtocol.EMBEDDING, embedding.protocol());
        assertEquals(List.of("text-embedding-3-small"), embedding.embeddingModelNames());
        assertEquals("https://api.openai.com/v1/embeddings", embedding.endpointUrl());
        var rerank = parser.find(config, AiEndpointUsage.RERANK, "openai").orElseThrow();
        assertEquals(List.of("bge-reranker-v2-m3"), rerank.rerankModelNames());
    }

    @Test
    void doesNotSynthesizeDuplicatesWhenExplicitEndpointExists() {
        String providers = """
                [
                  {
                    "code": "openai",
                    "baseUrl": "https://api.openai.com/v1",
                    "apiKey": "sk",
                    "embeddingModels": ["text-embedding-3-small"],
                    "models": [{ "code": "gpt-4o-mini", "model": "gpt-4o-mini" }]
                  },
                  {
                    "code": "openai",
                    "name": "自建向量化",
                    "usage": "embedding",
                    "baseUrl": "https://embedding.example.com/v1",
                    "apiKey": "sk-embedding",
                    "models": [{ "code": "custom-embed", "model": "custom-embed" }]
                  }
                ]
                """;
        var parser = new AiProviderConfigParser();
        Map<String, String> config = Map.of("providers", providers);

        var embeddings = parser.endpoints(config, AiEndpointUsage.EMBEDDING);
        assertEquals(1, embeddings.size());
        assertEquals("https://embedding.example.com/v1/embeddings", embeddings.get(0).endpointUrl());
    }

    @Test
    void keepsEndpointUsageWhenProtocolConflicts() {
        String providers = """
                [{
                  "code": "bge",
                  "usage": "embedding",
                  "protocol": "CHAT",
                  "baseUrl": "https://embedding.example.com/v1",
                  "apiKey": "sk",
                  "models": [{ "code": "bge-m3", "model": "bge-m3" }]
                }]
                """;
        var parser = new AiProviderConfigParser();
        var embedding = parser.find(Map.of("providers", providers), AiEndpointUsage.EMBEDDING, "bge").orElseThrow();

        // 用途优先：协议被修正为 EMBEDDING，端点仍留在向量化清单，默认路径为 /embeddings。
        assertEquals(AiProviderProtocol.EMBEDDING, embedding.protocol());
        assertEquals("/embeddings", embedding.completionsPath());
        assertTrue(embedding.isUsage(AiEndpointUsage.EMBEDDING));
        assertFalse(embedding.isChat());
    }

    @Test
    void reportsUsageBreakdownInMetrics() {
        Map<String, String> config = Map.of("providers", """
                [
                  { "code": "chat-a", "baseUrl": "https://a.example.com/v1", "apiKey": "k", "models": [{ "code": "m", "model": "m" }] },
                  { "code": "embed-a", "usage": "embedding", "baseUrl": "https://a.example.com/v1", "apiKey": "k", "models": [{ "code": "e", "model": "e" }] }
                ]
                """);
        var metrics = new AiProviderConfigParser().metrics(config);

        assertEquals(1, metrics.get("providerCount"));
        assertEquals(List.of("chat-a"), metrics.get("providers"));
        assertEquals(List.of("embed-a"), metrics.get("embeddingEndpoints"));
        assertEquals(1, metrics.get("embeddingModelCount"));
        assertEquals(List.of("chat"), metrics.get("protocols"));
    }
}