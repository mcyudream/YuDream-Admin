package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.handler.AgentLlmNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentModelToolResolver;
import online.yudream.base.application.platform.agent.workflow.support.AgentToolExecutor;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgentLlmNodeHandlerTest {

    @Test
    void llmShouldStreamTextAndExposeOnlyItsOwnTools() {
        CapturingGateway gateway = new CapturingGateway("answer", List.of());
        AgentWorkflowRunState state = state(gateway, List.of(tool("web.fetch"), tool("cms.patch")));

        Object result = execute("llm", """
                {"id":"model","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                "toolCodes":["web.fetch"],"toolMode":"AUTO"}}
                """, state, gateway);

        assertThat(result).isEqualTo("answer");
        assertThat(gateway.requests.getFirst().toolMode()).isEqualTo(AiToolMode.AUTO);
        assertThat(gateway.scopedToolNames).containsExactly(List.of("web.fetch"));
    }

    @Test
    void extractShouldRequireJsonAndConfiguredRequiredFields() {
        CapturingGateway gateway = new CapturingGateway("{\"title\":\"Agent\"}", List.of());
        AgentWorkflowRunState state = state(gateway, List.of());

        Object output = execute("extract", """
                {"id":"extract","data":{"kind":"extract","providerCode":"p","modelCode":"m",
                "outputSchema":{"type":"object","required":["title"]}}}
                """, state, gateway);

        assertThat(output).isEqualTo(Map.of("title", "Agent"));

        CapturingGateway missing = new CapturingGateway("{\"other\":true}", List.of());
        assertThatThrownBy(() -> execute("extract", """
                {"id":"extract","data":{"kind":"extract","providerCode":"p","modelCode":"m",
                "outputSchema":{"type":"object","required":["title"]}}}
                """, state(missing, List.of()), missing))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("title");
    }

    @Test
    void classifyShouldOnlyReturnOneConfiguredClass() {
        CapturingGateway gateway = new CapturingGateway("refund", List.of());
        assertThat(execute("classify", """
                {"id":"classify","data":{"kind":"classify","providerCode":"p","modelCode":"m",
                "classes":["refund","consult"]}}
                """, state(gateway, List.of()), gateway)).isEqualTo("refund");

        CapturingGateway invalid = new CapturingGateway("other", List.of());
        assertThatThrownBy(() -> execute("classify", """
                {"id":"classify","data":{"kind":"classify","providerCode":"p","modelCode":"m",
                "classes":["refund","consult"]}}
                """, state(invalid, List.of()), invalid))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分类");
    }

    @Test
    void visionShouldRequireImageAndPassItOnlyToVisionNode() {
        CapturingGateway gateway = new CapturingGateway("described", List.of());
        AgentWorkflowRunState state = state(gateway, List.of());
        state.command().setImageDataUrl("data:image/png;base64,image");

        execute("vision", """
                {"id":"vision","data":{"kind":"vision","providerCode":"p","modelCode":"vision-model"}}
                """, state, gateway);

        assertThat(gateway.requests.getFirst().imageDataUrl()).isEqualTo("data:image/png;base64,image");

        CapturingGateway noImage = new CapturingGateway("unused", List.of());
        assertThatThrownBy(() -> execute("vision", """
                {"id":"vision","data":{"kind":"vision","providerCode":"p","modelCode":"vision-model"}}
                """, state(noImage, List.of()), noImage))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("图片");
    }

    @Test
    void llmMustNotAcceptImageEvenWhenLegacyVisionFlagIsPresent() {
        CapturingGateway gateway = new CapturingGateway("text only", List.of());
        AgentWorkflowRunState state = state(gateway, List.of());
        state.command().setImageDataUrl("data:image/png;base64,image");

        execute("llm", """
                {"id":"llm","data":{"kind":"llm","providerCode":"p","modelCode":"m","vision":true}}
                """, state, gateway);

        assertThat(gateway.requests.getFirst().imageDataUrl()).isNull();
    }

    @Test
    void extractShouldUseExtractionErrorWhenModelReturnsInvalidJson() {
        CapturingGateway gateway = new CapturingGateway("not json", List.of());

        assertThatThrownBy(() -> execute("extract", """
                {"id":"extract","data":{"kind":"extract","providerCode":"p","modelCode":"m"}}
                """, state(gateway, List.of()), gateway))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("信息提取");
    }

    @Test
    void requiredToolModeShouldFailWhenGenerationHasNoToolResult() {
        CapturingGateway gateway = new CapturingGateway("answer", List.of());
        assertThatThrownBy(() -> execute("llm", """
                {"id":"llm","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                "toolCodes":["web.fetch"],"toolMode":"REQUIRED"}}
                """, state(gateway, List.of(tool("web.fetch"))), gateway))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须调用工具");
    }

    @Test
    void differentModelNodesMustNotLeakToolsAndLegacyUnderstandKeepsTolerantJson() {
        CapturingGateway gateway = new CapturingGateway("not json", List.of());
        AgentWorkflowRunState state = state(gateway, List.of(tool("web.fetch"), tool("cms.patch")));

        execute("llm", """
                {"id":"first","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                "toolCodes":["web.fetch"]}}
                """, state, gateway);
        execute("llm", """
                {"id":"second","data":{"kind":"llm","providerCode":"p","modelCode":"m",
                "toolCodes":["cms.patch"]}}
                """, state, gateway);
        Object understand = execute("understand", """
                {"id":"understand","data":{"kind":"understand","providerCode":"p","modelCode":"m",
                "strictJson":false}}
                """, state, gateway);

        assertThat(gateway.scopedToolNames).containsExactly(List.of("web.fetch"), List.of("cms.patch"), List.of());
        assertThat(understand).isEqualTo(Map.of("raw", "not json"));
    }

    @Test
    void runStateMustPreserveIndependentEqualToolEvents() {
        List<AiAgentToolResult> events = new ArrayList<>();
        AgentWorkflowRunState state = state(new CapturingGateway("unused", List.of()), List.of(), events::add);
        AiAgentToolResult first = new AiAgentToolResult("web.fetch", "fetch", "", "done", Map.of("url", "a"));
        AiAgentToolResult second = new AiAgentToolResult("web.fetch", "fetch", "", "done", Map.of("url", "a"));

        state.addToolResult(first);
        state.addToolResult(second);

        assertThat(state.toolResults()).containsExactly(first, second);
        assertThat(events).containsExactly(first, second);
    }

    @Test
    void handlerMustNotRepeatTheSameCallbackResultReturnedByGateway() {
        AiAgentToolResult toolResult = new AiAgentToolResult("web.fetch", "fetch", "", "done", Map.of());
        List<AiAgentToolResult> events = new ArrayList<>();
        CapturingGateway gateway = new CapturingGateway("answer", List.of(toolResult));
        AgentWorkflowRunState state = state(gateway, List.of(), events::add);

        execute("llm", """
                {"id":"llm","data":{"kind":"llm","providerCode":"p","modelCode":"m"}}
                """, state, gateway);

        assertThat(state.toolResults()).containsExactly(toolResult);
        assertThat(events).containsExactly(toolResult);
    }

    @Test
    void handlerMustMatchEqualFinalToolResultAsTheAlreadyStreamedCallback() {
        AiAgentToolResult callbackResult = new AiAgentToolResult("web.fetch", "fetch", "", "done", Map.of("url", "a"));
        AiAgentToolResult finalResult = new AiAgentToolResult("web.fetch", "fetch", "", "done", Map.of("url", "a"));
        List<AiAgentToolResult> events = new ArrayList<>();
        AiGenerationGateway gateway = new AiGenerationGateway() {
            @Override public AiGenerationResult generate(AiGenerationRequest request) {
                return new AiGenerationResult(null, "answer", null, null, null, null, null, List.of(), List.of(finalResult));
            }
            @Override public AiGenerationResult generateStream(AiGenerationRequest request,
                    java.util.function.Consumer<String> onDelta,
                    java.util.function.Consumer<AiAgentToolResult> onTool,
                    java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> onProgress) {
                onTool.accept(callbackResult);
                return generate(request);
            }
        };
        AgentWorkflowRunState state = state(gateway, List.of(), events::add);

        execute("llm", """
                {"id":"llm","data":{"kind":"llm","providerCode":"p","modelCode":"m"}}
                """, state, gateway);

        assertThat(state.toolResults()).containsExactly(callbackResult);
        assertThat(events).containsExactly(callbackResult);
    }

    private Object execute(String kind, String nodeJson, AgentWorkflowRunState state, AiGenerationGateway gateway) {
        ObjectMapper mapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(mapper);
        AgentWorkflowNode node;
        try {
            var raw = mapper.readTree(nodeJson);
            node = new AgentWorkflowNode(raw.path("id").asText(), kind, kind, raw.path("data"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        return new AgentLlmNodeHandler(kind, values, mapper, gateway, state).execute(node, new AgentWorkflowContext("input"))
                .output();
    }

    private AgentWorkflowRunState state(AiGenerationGateway gateway, List<AiAgentTool> tools) {
        return state(gateway, tools, ignored -> { });
    }

    private AgentWorkflowRunState state(
            AiGenerationGateway gateway,
            List<AiAgentTool> tools,
            java.util.function.Consumer<AiAgentToolResult> onTool
    ) {
        AgentApplication application = AgentApplication.builder().name("Agent").code("agent")
                .toolCodes(tools.stream().map(item -> item.descriptor().name()).toList()).build();
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("input");
        command.setPermissionContextExplicit(true);
        AgentToolExecutor executor = new AgentToolExecutor(new ObjectMapper(), mock(RuntimeExecutor.class),
                mock(AgentToolRepo.class), tools, permission -> true);
        return new AgentWorkflowRunState(application, command, Map.of(), java.util.Set.of(), ignored -> { }, onTool,
                new AgentModelToolResolver(executor));
    }

    private AiAgentTool tool(String name) {
        return new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() {
                return new AiAgentToolDescriptor(name, name, "", "", "", "", "", Map.of());
            }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) {
                return new AiAgentToolResult(name, name, "", "completed", Map.of());
            }
        };
    }

    private static final class CapturingGateway implements AiGenerationGateway {
        private final String summary;
        private final List<AiAgentToolResult> toolResults;
        private final List<AiGenerationRequest> requests = new ArrayList<>();
        private final List<List<String>> scopedToolNames = new ArrayList<>();

        private CapturingGateway(String summary, List<AiAgentToolResult> toolResults) {
            this.summary = summary;
            this.toolResults = toolResults;
        }

        @Override public AiGenerationResult generate(AiGenerationRequest request) {
            requests.add(request);
            scopedToolNames.add(AiAgentToolExecutionScope.currentTools().stream()
                    .map(tool -> tool.descriptor().name()).toList());
            return new AiGenerationResult(null, summary, null, null, null, null, null, List.of(), toolResults);
        }

        @Override public AiGenerationResult generateStream(AiGenerationRequest request,
                                                           java.util.function.Consumer<String> onDelta,
                                                           java.util.function.Consumer<AiAgentToolResult> onTool,
                                                           java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> onProgress) {
            AiGenerationResult result = generate(request);
            onDelta.accept(summary);
            toolResults.forEach(onTool);
            return result;
        }
    }
}
