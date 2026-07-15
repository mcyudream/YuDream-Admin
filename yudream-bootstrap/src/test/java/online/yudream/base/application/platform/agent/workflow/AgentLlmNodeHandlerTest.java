package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentLlmNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLlmNodeHandlerTest {

    @Test
    void shouldExecuteUnderstandThenLlmWithNodeSpecificModelsAndVision() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        CapturingGateway gateway = new CapturingGateway();
        AgentApplication application = AgentApplication.builder()
                .name("客服 Agent")
                .code("support")
                .systemPrompt("你是客服")
                .toolCodes(List.of("web.fetch"))
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("如何退款");
        cmd.setImageDataUrl("data:image/png;base64,abc");
        List<String> deltas = new ArrayList<>();
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application,
                cmd,
                Map.of("providers", "[]"),
                java.util.Set.of("web.fetch"),
                deltas::add,
                ignored -> { }
        );
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(
                        new AgentStartNodeHandler(values),
                        new AgentLlmNodeHandler("understand", values, objectMapper, gateway, state),
                        new AgentLlmNodeHandler("llm", values, objectMapper, gateway, state),
                        new AgentEndNodeHandler(values)
                )
        );

        AgentWorkflowExecution execution = executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                  {"id":"understand","data":{"kind":"understand","prompt":"提取 intent","inputVariable":"query","outputVariable":"intent","providerCode":"p1","modelCode":"m1"}},
                  {"id":"llm","data":{"kind":"llm","prompt":"回答用户","inputVariable":"intent","outputVariable":"answer","providerCode":"p2","modelCode":"m2","vision":true}},
                  {"id":"end","data":{"kind":"end","inputVariable":"answer"}}
                ],"edges":[
                  {"source":"start","target":"understand"},
                  {"source":"understand","target":"llm"},
                  {"source":"llm","target":"end"}
                ]}
                """, cmd.getInput());

        assertThat(gateway.requests).hasSize(2);
        assertThat(gateway.requests.get(0).providerCode()).isEqualTo("p1");
        assertThat(gateway.requests.get(0).modelCode()).isEqualTo("m1");
        assertThat(gateway.requests.get(0).imageDataUrl()).isNull();
        assertThat(gateway.requests.get(1).providerCode()).isEqualTo("p2");
        assertThat(gateway.requests.get(1).modelCode()).isEqualTo("m2");
        assertThat(gateway.requests.get(1).imageDataUrl()).isEqualTo(cmd.getImageDataUrl());
        assertThat(gateway.requests.get(1).userPrompt()).contains("refund");
        assertThat(gateway.requests.get(1).toolCallingEnabled()).isTrue();
        assertThat(deltas).containsExactly("最终回答");
        assertThat(execution.context().nodeOutput("end")).isEqualTo("最终回答");
    }

    @Test
    void shouldAllowPluginRuntimeToOverrideModelAndEnablePluginToolCalling() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        CapturingGateway gateway = new CapturingGateway();
        AgentApplication application = AgentApplication.builder()
                .name("插件 Agent")
                .code("plugin-agent")
                .toolCodes(List.of())
                .build();
        AgentRunCmd cmd = new AgentRunCmd();
        cmd.setInput("hello");
        cmd.setProviderCode("runtime-provider");
        cmd.setModelCode("runtime-model");
        cmd.setRuntimeToolCallingEnabled(true);
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application, cmd, Map.of(), java.util.Set.of(), ignored -> { }, ignored -> { }
        );
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                new AgentWorkflowGraphParser(objectMapper),
                List.of(
                        new AgentStartNodeHandler(values),
                        new AgentLlmNodeHandler("llm", values, objectMapper, gateway, state),
                        new AgentEndNodeHandler(values)
                )
        );

        executor.execute("""
                {"nodes":[
                  {"id":"start","data":{"kind":"start","outputVariable":"query"}},
                  {"id":"llm","data":{"kind":"llm","inputVariable":"query","outputVariable":"answer","providerCode":"__default__","modelCode":"__default__","allowModelOverride":true}},
                  {"id":"end","data":{"kind":"end","inputVariable":"answer"}}
                ],"edges":[
                  {"source":"start","target":"llm"},{"source":"llm","target":"end"}
                ]}
                """, cmd.getInput());

        AiGenerationRequest request = gateway.requests.getFirst();
        assertThat(request.providerCode()).isEqualTo("runtime-provider");
        assertThat(request.modelCode()).isEqualTo("runtime-model");
        assertThat(request.toolCallingEnabled()).isTrue();
    }

    private static final class CapturingGateway implements AiGenerationGateway {
        private final List<AiGenerationRequest> requests = new ArrayList<>();

        @Override
        public AiGenerationResult generate(AiGenerationRequest request) {
            requests.add(request);
            String summary = "m1".equals(request.modelCode()) ? "{\"intent\":\"refund\"}" : "最终回答";
            return new AiGenerationResult(null, summary, null, null, null, null, null, List.of(), List.of());
        }

        @Override
        public AiGenerationResult generateStream(
                AiGenerationRequest request,
                java.util.function.Consumer<String> onDelta,
                java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> onTool,
                java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress> onProgress
        ) {
            AiGenerationResult result = generate(request);
            onDelta.accept(result.summary());
            return result;
        }
    }
}
