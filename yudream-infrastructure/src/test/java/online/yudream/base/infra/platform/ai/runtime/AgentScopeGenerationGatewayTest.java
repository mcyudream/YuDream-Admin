package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.Msg;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AbstractOpenAiCompatibleProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderType;
import online.yudream.base.infra.platform.ai.service.provider.AiModelEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeGenerationGatewayTest {

    private final PluginAiToolRegistry pluginRegistry = new PluginAiToolRegistry();

    @Test
    void streamsDeltasAndReasoningFromDirectModelPath() {
        FakeModel fake = new FakeModel(List.of(
                List.of(
                        chunk(new ContentBlock[] {ThinkingBlock.builder().thinking("思考中").build()}),
                        chunk(new ContentBlock[] {TextBlock.builder().text("你好").build()}),
                        chunk(new ContentBlock[] {TextBlock.builder().text("，世界").build()},
                                new ChatUsage(10, 5, 0.1)))
        ));
        AgentScopeGenerationGateway gateway = gateway(fake);

        StringBuilder deltas = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        AiGenerationResult result = gateway.generateStream(
                request("你好", false),
                deltas::append,
                reasoning::append,
                null,
                null
        );

        assertEquals("思考中", reasoning.toString());
        assertEquals("你好，世界", deltas.toString());
        assertEquals("你好，世界", result.summary());
        assertEquals(15, result.usage().totalTokens());
        assertEquals(1, fake.calls.get());
    }

    @Test
    void runsToolLoopThroughAgentAndCollectsResults() {
        FakeModel fake = new FakeModel(List.of(
                List.of(chunk(new ContentBlock[] {ToolUseBlock.builder()
                        .id("call-1")
                        .name("wiki_search")
                        .input(java.util.Map.of("query", "测试"))
                        .build()})),
                List.of(chunk(new ContentBlock[] {TextBlock.builder().text("检索完成，结论如下。").build()},
                        new ChatUsage(20, 8, 0.2)))
        ));
        AtomicInteger executed = new AtomicInteger();
        AiAgentTool tool = new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor("wiki.search", "知识检索", "检索知识库",
                        null, "知识检索", "平台工具", "检索", java.util.Map.of("query", "查询词"), null);
            }

            @Override
            public AgentToolRisk risk() {
                return AgentToolRisk.READ;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                executed.incrementAndGet();
                assertEquals("测试", call.arguments().get("query"));
                return new AiAgentToolResult("wiki.search", "search", null, "命中 1 条", java.util.Map.of("hits", 1));
            }
        };
        AgentScopeGenerationGateway gateway = gateway(fake, tool);

        List<AiAgentToolResult> toolCallbacks = new ArrayList<>();
        AiGenerationResult result = gateway.generateStream(
                new AiGenerationRequest("你是助手", "查一下", (String) null, java.util.List.<String>of(), null, null,
                        TEST_CONFIG, java.util.List.<online.yudream.base.domain.platform.ai.valobj.AiChatMessage>of(),
                        true, AiToolMode.AUTO, AiStructuredOutput.none()),
                null,
                null,
                toolCallbacks::add,
                null
        );

        assertEquals(1, executed.get());
        assertEquals(1, toolCallbacks.size());
        assertEquals("wiki.search", toolCallbacks.getFirst().toolName());
        assertEquals("检索完成，结论如下。", result.summary());
        assertEquals(1, result.toolResults().size());
        assertEquals(28, result.usage().totalTokens());
        assertTrue(fake.calls.get() >= 2);
    }

    @Test
    void preservesFinalModelTextWhenToolResultsExist() {
        var result = AgentScopeGenerationGateway.toResult(
                "已抓取参考页面，并据此完成分析。",
                List.of(new AiAgentToolResult("web.fetch", "fetch", "permission", "网页参考内容已抓取。", Map.of())),
                online.yudream.base.domain.platform.ai.valobj.AiUsage.empty()
        );
        assertEquals("已抓取参考页面，并据此完成分析。", result.summary());
        assertEquals(1, result.toolResults().size());
    }

    @Test
    void fallsBackToFirstToolMessageWhenContentBlank() {
        var result = AgentScopeGenerationGateway.toResult(
                "",
                List.of(new AiAgentToolResult("cms.canvas.patch", "patch", null, "画布操作已完成。", Map.of())),
                online.yudream.base.domain.platform.ai.valobj.AiUsage.empty()
        );
        assertEquals("画布操作已完成。", result.summary());
    }

    @Test
    void structuredSchemaOutputReturnsJsonContent() {
        // native 结构化路径：模型按 response_format 直接返回 JSON 文本，框架解析后挂载
        // _structured_output 元数据，网关序列化为内容字符串。
        FakeModel fake = new FakeModel(List.of(
                List.of(chunk(new ContentBlock[] {TextBlock.builder()
                        .text("{\"city\":\"上海\",\"temperature\":23}").build()},
                        new ChatUsage(12, 6, 0.1)))
        ));
        AgentScopeGenerationGateway gateway = gateway(fake);

        AiGenerationResult result = gateway.generate(new AiGenerationRequest(
                "抽取城市", "上海今天 23 度", (String) null, java.util.List.<String>of(), null, null,
                TEST_CONFIG, java.util.List.<online.yudream.base.domain.platform.ai.valobj.AiChatMessage>of(),
                false, AiToolMode.NONE,
                AiStructuredOutput.jsonSchema("weather",
                        java.util.Map.of("type", "object",
                                "properties", java.util.Map.of(
                                        "city", java.util.Map.of("type", "string"),
                                        "temperature", java.util.Map.of("type", "number"))),
                        false)
        ));

        assertTrue(result.summary().contains("上海"), "structured output should serialize into content: calls=" + fake.calls.get() + " summary=[" + result.summary() + "] toolResults=" + result.toolResults().size());
    }

    // ------------------------------------------------------------------
    // 装配辅助
    // ------------------------------------------------------------------

    private static final Map<String, String> TEST_CONFIG = Map.of("providers", """
            [{"code":"test","name":"测试供应商","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.example.com/v1",
              "apiKey":"sk-test","defaultModel":"fake-model",
              "models":[{"code":"fake-model","name":"fake-model","model":"fake-model"}]}]
            """);

    private AgentScopeGenerationGateway gateway(FakeModel fake, AiAgentTool... tools) {
        AgentScopeModels models = new AgentScopeModels(new AiClientProperties()) {
            @Override
            public Model resolve(ResolvedAiModel resolved) {
                return fake;
            }
        };
        AiProviderAdapter adapter = new AbstractOpenAiCompatibleProviderAdapter() {
            @Override
            public AiProviderType type() {
                return AiProviderType.OPENAI_COMPATIBLE;
            }
        };
        ObjectProvider<AiAgentTool> toolProvider = new ObjectProvider<>() {
            @Override
            public AiAgentTool getObject() {
                return tools.length == 0 ? null : tools[0];
            }

            @Override
            public java.util.stream.Stream<AiAgentTool> stream() {
                return java.util.Arrays.stream(tools);
            }
        };
        return new AgentScopeGenerationGateway(
                models,
                new AiProviderConfigParser(),
                List.of(adapter),
                new AiClientProperties(),
                toolProvider,
                pluginRegistry
        );
    }

    private AiGenerationRequest request(String prompt, boolean toolCalling) {
        return new AiGenerationRequest("你是助手", prompt, (String) null, java.util.List.<String>of(), null, null,
                TEST_CONFIG,
                java.util.List.<online.yudream.base.domain.platform.ai.valobj.AiChatMessage>of(),
                toolCalling, toolCalling ? AiToolMode.AUTO : AiToolMode.NONE,
                AiStructuredOutput.none());
    }

    private static ChatResponse chunk(ContentBlock[] blocks) {
        return new ChatResponse("resp-1", List.of(blocks), null, Map.of(), null);
    }

    private static ChatResponse chunk(ContentBlock[] blocks, ChatUsage usage) {
        return new ChatResponse("resp-1", List.of(blocks), usage, Map.of(), "stop");
    }

    /**
     * 脚本化假模型：按调用次序回放响应脚本，供无网络环境下驱动直连与 Agent 循环。
     */
    static class FakeModel implements Model {
        private final Deque<List<ChatResponse>> script;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<List<ToolSchema>> lastTools = new AtomicReference<>(List.of());

        FakeModel(List<List<ChatResponse>> script) {
            this.script = new ArrayDeque<>(script);
        }

        @Override
        public reactor.core.publisher.Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            calls.incrementAndGet();
            lastTools.set(tools == null ? List.of() : tools);
            List<ChatResponse> next = script.poll();
            if (next == null) {
                return reactor.core.publisher.Flux.error(new IllegalStateException("脚本耗尽"));
            }
            return reactor.core.publisher.Flux.fromIterable(next);
        }

        @Override
        public String getModelName() {
            return "fake-model";
        }
    }
}
