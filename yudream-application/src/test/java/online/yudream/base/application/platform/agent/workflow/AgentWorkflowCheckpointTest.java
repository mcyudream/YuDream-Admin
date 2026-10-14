package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentWorkflowCheckpointTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String GRAPH = """
            {"nodes":[{"id":"start","data":{"kind":"start"}},
              {"id":"charge","data":{"kind":"tool"}},
              {"id":"model","data":{"kind":"llm"}},
              {"id":"end","data":{"kind":"end"}}],
             "edges":[{"source":"start","target":"charge"},
              {"source":"charge","target":"model"},{"source":"model","target":"end"}]}
            """;

    @Test void serializedWaitingFrameContinuesOnNewInstanceWithoutRepeatingSideEffects() throws Exception {
        var sideEffects = new AtomicInteger();
        var modelCalls = new AtomicInteger();
        var events = new java.util.ArrayList<String>();
        var listener = new AgentWorkflowEventListener() {
            @Override public void onNodeSuspended(AgentWorkflowNode node, AgentWorkflowContext context) { events.add("waiting:" + node.id()); }
            @Override public void onNodeFailed(AgentWorkflowNode node, RuntimeException error, AgentWorkflowContext context) { fail("等待工具不是节点失败"); }
            @Override public void onNodeSkipped(AgentWorkflowNode node, AgentWorkflowContext context) { fail("暂停后不能把剩余节点判为跳过"); }
        };
        var initial = executor(sideEffects, modelCalls).execute(GRAPH, "用户输入", listener);
        assertTrue(initial.waiting());
        assertEquals(List.of("start", "charge"), initial.executedNodeIds());
        assertEquals(List.of("waiting:model"), events);
        assertNull(initial.context().nodeOutput("end"));
        var recovered = json.readValue(json.writeValueAsString(initial.checkpoint()), AgentWorkflowFrame.class);
        var completed = executor(sideEffects, modelCalls).resume(GRAPH, recovered,
                AgentWorkflowNodeResult.output("审批后的真实回答").withVariables(Map.of("answer", "审批后的真实回答")), null);
        assertFalse(completed.waiting());
        assertEquals("审批后的真实回答", completed.context().nodeOutput("end"));
        assertEquals("用户输入", completed.context().variable("query"));
        assertEquals(Map.of("receipt", "唯一凭据"), completed.context().nodeOutput("charge"));
        assertEquals(1, sideEffects.get());
        assertEquals(1, modelCalls.get(), "native 完成结果只能补回原节点，不能再次提交用户输入");
    }

    @Test void followingNativeNodeMaySuspendAgainWithoutLosingEarlierBusinessState() throws Exception {
        var effects = new AtomicInteger();
        var calls = new AtomicInteger();
        String graph = GRAPH.replace("{\"id\":\"end\",\"data\":{\"kind\":\"end\"}}",
                "{\"id\":\"second\",\"data\":{\"kind\":\"llm\"}},{\"id\":\"end\",\"data\":{\"kind\":\"end\"}}")
                .replace("{\"source\":\"model\",\"target\":\"end\"}",
                        "{\"source\":\"model\",\"target\":\"second\"},{\"source\":\"second\",\"target\":\"end\"}");
        var first = executor(effects, calls).execute(graph, "输入");
        var next = executor(effects, calls).resume(graph, first.checkpoint(),
                AgentWorkflowNodeResult.output("第一轮完成").withVariables(Map.of("answer", "第一轮完成")), null);
        assertTrue(next.waiting());
        assertEquals("second", next.checkpoint().pausedNodeId());
        assertEquals("第一轮完成", next.context().nodeOutput("model"));
        var restored = json.readValue(json.writeValueAsString(next.checkpoint()), AgentWorkflowFrame.class);
        var finalExecution = executor(effects, calls).resume(graph, restored,
                AgentWorkflowNodeResult.output("第二轮完成").withVariables(Map.of("answer", "第二轮完成")), null);
        assertEquals("第二轮完成", finalExecution.context().nodeOutput("end"));
        assertEquals(1, effects.get());
        assertEquals(2, calls.get());
    }

    @Test void mismatchedFrameFailsBeforeAnyBusinessNodeCanExecute() {
        var effects = new AtomicInteger();
        var calls = new AtomicInteger();
        var pending = executor(effects, calls).execute(GRAPH, "输入").checkpoint();
        var wrong = new AgentWorkflowFrame(0, "model", pending.input(), pending.activatedNodeIds(),
                pending.executedNodeIds(), pending.nodeOutputs(), pending.variables(), pending.suspendedNative());
        assertThrows(AgentWorkflowDefinitionException.class, () -> executor(effects, calls).resume(GRAPH, wrong,
                AgentWorkflowNodeResult.output("回答"), null));
        assertEquals(1, effects.get());
        assertEquals(1, calls.get());
    }

    private AgentWorkflowExecutor executor(AtomicInteger effects, AtomicInteger calls) {
        return new AgentWorkflowExecutor(new AgentWorkflowGraphParser(json), List.of(
                handler("start", (node, ctx) -> AgentWorkflowNodeResult.output(ctx.input()).withVariables(Map.of("query", ctx.input()))),
                handler("tool", (node, ctx) -> { effects.incrementAndGet(); return AgentWorkflowNodeResult.output(Map.of("receipt", "唯一凭据")); }),
                handler("llm", (node, ctx) -> { calls.incrementAndGet(); throw new AgentWorkflowSuspendedException(
                        new HarnessRunResult("", "", AiUsage.empty(), List.of(), List.of(), "turn-" + node.id(), "run-" + node.id(),
                                "suspended", List.of(new HarnessPendingInteraction("call-" + node.id(), "external_execution", "ui_navigate", Map.of())))); }),
                handler("end", (node, ctx) -> AgentWorkflowNodeResult.output(ctx.variable("answer")))));
    }
    private AgentWorkflowNodeHandler handler(String kind, java.util.function.BiFunction<AgentWorkflowNode, AgentWorkflowContext, AgentWorkflowNodeResult> action) {
        return new AgentWorkflowNodeHandler() {
            @Override public String kind() { return kind; }
            @Override public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) { return action.apply(node, context); }
        };
    }
}
