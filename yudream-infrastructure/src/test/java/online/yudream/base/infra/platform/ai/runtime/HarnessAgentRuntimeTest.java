package online.yudream.base.infra.platform.ai.runtime;

import com.mongodb.client.MongoClients;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderType;
import online.yudream.base.infra.platform.ai.service.provider.AbstractOpenAiCompatibleProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness 运行时集成测试：无网络 FakeModel + 内存 Mongo 桩，验证
 * 会话状态跨轮次持久化（框架 AgentState）、增量回调与按次工具开关。
 */
class HarnessAgentRuntimeTest extends MongoStoreTestBase {

    Path workspace;

    private RecordingFakeModel fake;
    private HarnessAgentGateway runtime;

    @BeforeEach
    void initRuntime() throws java.io.IOException {
        workspace = java.nio.file.Files.createTempDirectory("agent-workspace");
        fake = new RecordingFakeModel("你好");
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
        HarnessRuntimeProperties properties = new HarnessRuntimeProperties();
        properties.setWorkspaceDir(workspace.toString());
        properties.setMemoryHooksEnabled(false);
        HarnessAgentRuntime agentRuntime = new HarnessAgentRuntime(
                models,
                new AiProviderConfigParser(),
                List.of(adapter),
                new AiClientProperties(),
                properties,
                new MongoAgentStateStore(mongo),
                new MongoBaseStore(mongo),
                new AgentPermissionGateway() {
                    @Override
                    public boolean hasPermission(String permissionCode) {
                        return true;
                    }

                    @Override
                    public boolean hasPermission(String permissionCode, List<String> permissionCodes, boolean explicitContext) {
                        return true;
                    }
                },
                new ObjectProvider<online.yudream.base.domain.platform.ai.service.AiAgentTool>() {
                    @Override
                    public online.yudream.base.domain.platform.ai.service.AiAgentTool getObject() {
                        return null;
                    }

                    @Override
                    public Stream<online.yudream.base.domain.platform.ai.service.AiAgentTool> stream() {
                        return Stream.empty();
                    }
                });
        runtime = agentRuntime;
    }

    @Test
    void secondTurnSeesFirstTurnContextThroughAgentState() {
        HarnessRunRequest first = new HarnessRunRequest(
                1L, "s-1", "general", "测试提示词", "我叫天宇",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON),
                false, List.of(), List.of());
        HarnessRunResult firstResult = runtime.run(first, null, null, null);
        assertEquals("你好", firstResult.content());
        assertTrue(fake.firstCallTexts().stream().anyMatch(text -> text.contains("我叫天宇")),
                "first turn should contain user message: " + fake.firstCallTexts());
        assertTrue(fake.firstCallTexts().stream().anyMatch(text -> text.contains("测试提示词")),
                "system prompt override should reach the model");

        HarnessRunRequest second = new HarnessRunRequest(
                1L, "s-1", "general", "测试提示词", "我叫什么名字？",
                List.of(), "test", "fake-model", Map.of("providers", PROVIDERS_JSON),
                false, List.of(), List.of());
        HarnessRunResult secondResult = runtime.run(second, null, null, null);
        assertEquals("你好", secondResult.content());

        List<String> secondCallTexts = fake.callTexts(1);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("我叫天宇")),
                "framework should restore first-turn user message from AgentState: " + secondCallTexts);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("你好")),
                "framework should restore first-turn assistant reply from AgentState: " + secondCallTexts);
        assertTrue(secondCallTexts.stream().anyMatch(text -> text.contains("我叫什么名字？")),
                "second turn should contain new user message");
    }

    @Test
    void differentSessionsDoNotShareContext() {
        HarnessRunRequest alice = new HarnessRunRequest(
                1L, "s-a", "general", "提示词", "天宇的问题", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(alice, null, null, null);

        HarnessRunRequest bob = new HarnessRunRequest(
                2L, "s-a", "general", "提示词", "天宇是谁", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(bob, null, null, null);

        List<String> bobCallTexts = fake.callTexts(1);
        assertTrue(bobCallTexts.stream().noneMatch(text -> text.contains("天宇的问题")),
                "different users must not share session context: " + bobCallTexts);
    }

    @Test
    void toolCallingDisabledHidesAllToolSchemas() {
        HarnessRunRequest request = new HarnessRunRequest(
                1L, "s-tools", "general", "提示词", "看看工具", List.of(),
                "test", "fake-model", Map.of("providers", PROVIDERS_JSON), false, List.of(), List.of());
        runtime.run(request, null, null, null);
        assertTrue(fake.lastTools().isEmpty(), "toolCallingEnabled=false must hide tools from the model");
    }

    private static final String PROVIDERS_JSON = """
            [{"code":"test","name":"测试","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.example.com/v1",
              "apiKey":"sk-test","defaultModel":"fake-model",
              "models":[{"code":"fake-model","name":"fake-model","model":"fake-model"}]}]
            """;

    /**
     * 记录型假模型：固定回复，记录每次调用的消息文本与工具清单；脚本耗尽时回退默认响应
     * （吸收框架后台记忆提炼等辅助调用）。
     */
    static class RecordingFakeModel implements Model {
        private final String reply;
        private final List<List<String>> calls = new CopyOnWriteArrayList<>();
        private volatile List<ToolSchema> lastTools = List.of();

        RecordingFakeModel(String reply) {
            this.reply = reply;
        }

        @Override
        public reactor.core.publisher.Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            List<String> texts = new ArrayList<>();
            for (Msg message : messages) {
                texts.add((message.getRole() == MsgRole.SYSTEM ? "[system] " : "") + message.getTextContent());
            }
            calls.add(texts);
            lastTools = tools == null ? List.of() : tools;
            return reactor.core.publisher.Flux.just(new ChatResponse("r",
                    List.<ContentBlock>of(TextBlock.builder().text(reply).build()), null, Map.of(), "stop"));
        }

        @Override
        public String getModelName() {
            return "fake-model";
        }

        List<String> firstCallTexts() {
            return callTexts(0);
        }

        List<String> callTexts(int index) {
            return calls.size() > index ? calls.get(index) : List.of();
        }

        List<ToolSchema> lastTools() {
            return lastTools;
        }
    }
}
