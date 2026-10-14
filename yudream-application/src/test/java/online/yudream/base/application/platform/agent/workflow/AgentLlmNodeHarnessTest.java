package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.handler.AgentLlmNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.service.HarnessExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AgentLlmNodeHarnessTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void repeatedNodeGetsDifferentTurnKeyAndOrdinaryChatCannotEnableWorkspaceTools() throws Exception {
        var keys = new ArrayList<String>();
        var requests = new ArrayList<online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest>();
        HarnessAgentGateway harness = (request, delta, reasoning, tool) -> {
            keys.add(HarnessExecutionScope.currentIdempotencyKey());
            requests.add(request);
            delta.accept("真实回答");
            return new HarnessRunResult("真实回答", "", AiUsage.empty(), List.of());
        };
        var cmd = command();
        cmd.setSessionId("conversation-1");
        cmd.setUserId(77L);
        var handler = handler("llm", cmd, noBlockingGateway(), harness, new ArrayList<>());
        var node = node("llm", "{\"toolMode\":\"NONE\"}");
        try (var scope = HarnessExecutionScope.open("run-1", ignored -> {})) {
            handler.execute(node, new AgentWorkflowContext("first"));
            handler.execute(node, new AgentWorkflowContext("second"));
        }
        assertEquals(List.of("run-1:model:1", "run-1:model:2"), keys);
        assertEquals(requests.get(0).sessionId(), requests.get(1).sessionId());
        assertEquals("agent-chat-demo", requests.get(0).agentCode());
        assertTrue(requests.get(0).allowedToolNames().isEmpty());
        assertEquals(77L, requests.get(0).userId());
        assertNull(HarnessExecutionScope.currentIdempotencyKey());
    }

    @Test void structuredUnderstandUsesRealStreamBeforeFinalParsing() throws Exception {
        var deltas = new ArrayList<String>();
        AiGenerationGateway streaming = new AiGenerationGateway() {
            @Override public AiGenerationResult generate(AiGenerationRequest request) { fail("不能回落阻塞生成"); return null; }
            @Override public AiGenerationResult generateStream(AiGenerationRequest request, Consumer<String> delta,
                    Consumer<String> reasoning, Consumer<AiAgentToolResult> tool, Consumer<AiGenerationProgress> progress) {
                assertNull(delta, "理解节点的内部 JSON 不应混入最终回答正文");
                deltas.add("模型真实流式调用");
                return AiGenerationResult.of("{\"topic\":\"真实增量\"}", List.of(), AiUsage.empty());
            }
        };
        var handler = handler("understand", command(), streaming,
                (request, delta, reasoning, tool) -> { fail("无状态结构化节点不启用工作区"); return null; }, deltas);
        var result = handler.execute(node("understand", "{}"), new AgentWorkflowContext("input"));
        assertEquals(List.of("模型真实流式调用"), deltas);
        assertEquals(Map.of("topic", "真实增量"), result.output());
    }

    private AgentLlmNodeHandler handler(String kind, AgentRunCmd cmd, AiGenerationGateway gateway,
                                       HarnessAgentGateway harness, List<String> deltas) {
        var app = AgentApplication.create("演示", "demo");
        var state = new AgentWorkflowRunState(app, cmd, Map.of(), Set.of(), deltas::add, null, null,
                null, null, "stable-run");
        return new AgentLlmNodeHandler(kind, new AgentWorkflowValueResolver(json), json, gateway, harness, state);
    }
    private AgentRunCmd command() {
        var cmd = new AgentRunCmd(); cmd.setProviderCode("provider"); cmd.setModelCode("model"); return cmd;
    }
    private AgentWorkflowNode node(String kind, String data) throws Exception {
        return new AgentWorkflowNode("model", kind, "模型", json.readTree(data));
    }
    private AiGenerationGateway noBlockingGateway() {
        return request -> { fail("有状态聊天必须由 Harness 管理"); return null; };
    }
}
