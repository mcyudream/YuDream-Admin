package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.repo.AgentWorkflowCheckpointRepo;
import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.ai.service.*;
import online.yudream.base.domain.platform.ai.valobj.*;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorkflowWaitingTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AtomicInteger sideEffects = new AtomicInteger();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final MemoryCheckpoints checkpoints = new MemoryCheckpoints();
    private final AgentApplication application = AgentApplication.builder().name("工作流").code("waiting")
            .toolCodes(List.of("receipt.issue", "demo.read"))
            .workflowJson("""
                    {"nodes":[{"id":"start","data":{"kind":"start","outputVariable":"query"}},
                      {"id":"receipt","data":{"kind":"tool","toolCode":"receipt.issue","inputVariable":"query","outputVariable":"receipt"}},
                      {"id":"model","data":{"kind":"llm","providerCode":"provider","modelCode":"model","inputVariable":"query",
                        "outputVariable":"answer","toolMode":"REQUIRED","toolCodes":["demo.read"]}},
                      {"id":"end","data":{"kind":"end","inputVariable":"answer"}}],
                     "edges":[{"source":"start","target":"receipt"},{"source":"receipt","target":"model"},{"source":"model","target":"end"}]}
                    """).build();

    @Test void requiredToolSuspensionPersistsAndFreshRuntimeContinuesOriginalGraphOnce() {
        var events = new java.util.ArrayList<AgentDebugEventDTO>();
        var command = command();
        try (var scope = HarnessExecutionScope.open("durable-graph", ignored -> {})) {
            assertEquals("前半", runtime().execute(application, command, Map.of(), events::add, null, null).content());
        }
        var stored = checkpoints.find("durable-graph").orElseThrow();
        assertEquals("WAITING", stored.status());
        assertEquals("model", stored.frame().pausedNodeId());
        assertEquals(1, sideEffects.get());
        assertEquals(1, modelCalls.get());
        assertTrue(events.stream().noneMatch(event -> "FAILED".equals(event.status()) || "SKIPPED".equals(event.status())));
        assertFalse(stored.command().containsKey("permissionCodes"));
        assertFalse(stored.command().containsKey("permissionContextExplicit"));
        var nativeDone = new HarnessRunResult("后半", "后续推理", new AiUsage(2, 1, 3),
                List.of(new AiAgentToolResult("demo.read", "complete", null, "读取完成", Map.of())),
                List.of(), "original-turn", "continued-run", "completed", List.of());
        try (var scope = HarnessExecutionScope.open("durable-graph", ignored -> {})) {
            var result = runtime().continueAfterNative(stored, application, command, Map.of(), nativeDone,
                    events::add, null, null, null, null);
            assertEquals("前半后半", result.content());
            assertEquals(5, result.usage().totalTokens());
            assertEquals("前段推理后续推理", result.reasoning());
        }
        assertEquals("COMPLETED", checkpoints.find("durable-graph").orElseThrow().status());
        assertEquals(1, sideEffects.get(), "换执行实例后不能重跑前置业务操作");
        assertEquals(1, modelCalls.get(), "原生完成结果不能重新调用模型入口");
        assertTrue(events.stream().anyMatch(event -> "end".equals(event.nodeId()) && "COMPLETED".equals(event.status())));
    }

    @Test void unrelatedNativeTurnCannotAdvanceCheckpoint() {
        try (var scope = HarnessExecutionScope.open("durable-graph", ignored -> {})) {
            runtime().execute(application, command(), Map.of(), null, null, null);
        }
        var stored = checkpoints.find("durable-graph").orElseThrow();
        var wrong = new HarnessRunResult("错误回复", "", AiUsage.empty(), List.of(), List.of(), "different-turn", "run", "completed", List.of());
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> runtime().continueAfterNative(stored, application, command(), Map.of(), wrong, null, null, null, null, null));
        assertEquals("WAITING", checkpoints.find("durable-graph").orElseThrow().status());
        assertEquals(1, sideEffects.get());
    }

    @Test void repeatedWaitingPhasesPersistOnceAcrossInstancesAndRetainAllRealOutputAndUsage() {
        try (var scope = HarnessExecutionScope.open("durable-graph", ignored -> {})) {
            runtime().execute(application, command(), Map.of(), null, null, null);
        }
        var original = checkpoints.find("durable-graph").orElseThrow();
        var phase = new HarnessRunResult("中段", "中段推理", new AiUsage(3, 1, 4),
                List.of(new AiAgentToolResult("demo.read", "complete", null, "第一工具已完成", Map.of())), List.of(),
                "original-turn", "second-native-run", "suspended", List.of(new HarnessPendingInteraction("next-call", "confirmation", "demo_read", Map.of())));
        var run = online.yudream.base.domain.platform.ai.aggregate.AiRun.start(77L, null, "agent", "instance");
        run.setId("durable-graph");
        var applications = mock(online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo.class);
        var runtimeApplications = mock(online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry.class);
        when(applications.findByCode("waiting")).thenReturn(Optional.of(application));
        when(runtimeApplications.findByCode("waiting")).thenReturn(Optional.empty());
        var continuation = new WorkflowAiRunContinuation(checkpoints, applications, runtimeApplications, runtime(),
                mock(CapabilityAppService.class), mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class), json);
        assertTrue(continuation.continueAfterNative(run, phase, java.util.Set.of("*"), null).isEmpty());
        int saved = checkpoints.saves;
        var newInstance = new WorkflowAiRunContinuation(checkpoints, applications, runtimeApplications, runtime(),
                mock(CapabilityAppService.class), mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class), json);
        assertTrue(newInstance.continueAfterNative(run, phase, java.util.Set.of("*"), null).isEmpty());
        assertTrue(newInstance.continueAfterNative(run, original.frame().suspendedNative(), java.util.Set.of("*"), null).isEmpty());
        assertEquals(saved, checkpoints.saves, "重复或迟到的已知执行阶段不能再次累加");
        var restored = checkpoints.find("durable-graph").orElseThrow();
        assertEquals(java.util.Set.of("initial-run", "second-native-run"), restored.nativePhaseRunIds());
        assertEquals("前半中段", restored.frame().suspendedNative().content());
        assertEquals(6, restored.usage().totalTokens());
        var done = new HarnessRunResult("后半", "后续推理", new AiUsage(2, 1, 3), List.of(), List.of(),
                "original-turn", "third-native-run", "completed", List.of());
        try (var scope = HarnessExecutionScope.open("durable-graph", ignored -> {})) {
            var completed = runtime().continueAfterNative(restored, application, command(), Map.of(), done, null, null, null, null, null);
            assertEquals("前半中段后半", completed.content());
            assertEquals("前段推理中段推理后续推理", completed.reasoning());
            assertEquals(9, completed.usage().totalTokens());
            assertEquals(2, completed.toolResults().size());
        }
        assertEquals(1, sideEffects.get());
        assertEquals(1, modelCalls.get());
    }

    private AgentWorkflowRuntimeService runtime() {
        HarnessAgentGateway harness = (request, delta, reasoning, tool) -> {
            modelCalls.incrementAndGet();
            assertEquals("durable-graph:model:1", HarnessExecutionScope.currentIdempotencyKey());
            if (delta != null) delta.accept("前半");
            if (reasoning != null) reasoning.accept("前段推理");
            return new HarnessRunResult("前半", "前段推理", new AiUsage(1, 1, 2), List.of(), List.of(), "original-turn", "initial-run",
                    "suspended", List.of(new HarnessPendingInteraction("pending-call", "external_execution", "demo_read", Map.of())));
        };
        AiGenerationGateway generation = request -> { throw new AssertionError("选工具的 LLM 应使用原生持久会话"); };
        var props = new AgentTraceProperties(); props.setEnabled(false);
        var tracer = new AgentExecutionTracer(props, mock(online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo.class),
                mock(online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry.class), event -> {}, json);
        var service = new AgentWorkflowRuntimeService(json, mock(RuntimeExecutor.class), mock(AgentToolRepo.class),
                provider(generation), provider(harness), provider(tool("receipt.issue", true), tool("demo.read", false)),
                mock(AgentKnowledgeOperations.class), mock(DocumentTextExtractor.class), AgentWorkflowWaitingTest.<AgentPluginToolGateway>provider(),
                permission -> true, mock(CapabilityAppService.class), tracer);
        service.setCheckpoints(checkpoints);
        return service;
    }
    private AiAgentTool tool(String name, boolean effect) {
        return new AiAgentTool() {
            @Override public AiAgentToolDescriptor descriptor() { return new AiAgentToolDescriptor(name, name, name, null, name, "测试", name, Map.of()); }
            @Override public AgentToolRisk risk() { return effect ? AgentToolRisk.WRITE : AgentToolRisk.READ; }
            @Override public AiAgentToolResult execute(AiAgentToolCall call) {
                if (effect) sideEffects.incrementAndGet();
                return new AiAgentToolResult(name, "complete", null, "已完成", Map.of("receipt", "唯一凭据"));
            }
        };
    }
    private AgentRunCmd command() { var cmd = new AgentRunCmd(); cmd.setInput("用户输入"); cmd.setUserId(77L); cmd.setPermissionCodes(List.of("*")); cmd.setPermissionContextExplicit(true); return cmd; }
    @SafeVarargs private static <T> ObjectProvider<T> provider(T... values) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return values.length == 0 ? null : values[0]; }
            @Override public Stream<T> stream() { return java.util.Arrays.stream(values); }
        };
    }
    private class MemoryCheckpoints implements AgentWorkflowCheckpointRepo {
        private final java.util.Map<String, String> rows = new java.util.HashMap<>();
        private int saves;
        @Override public Optional<AgentWorkflowCheckpoint> find(String id) {
            try { return Optional.ofNullable(rows.get(id)).map(value -> { try { return json.readValue(value, AgentWorkflowCheckpoint.class); } catch (Exception e) { throw new IllegalStateException(e); } }); }
            catch (RuntimeException e) { throw e; }
        }
        @Override public void save(AgentWorkflowCheckpoint value) { try { rows.put(value.runId(), json.writeValueAsString(value)); saves++; } catch (Exception e) { throw new IllegalStateException(e); } }
        @Override public void complete(String id) { finish(id, "COMPLETED"); }
        @Override public void cancel(String id) { finish(id, "CANCELLED"); }
        private void finish(String id, String status) { var value = find(id).orElseThrow(); save(new AgentWorkflowCheckpoint(value.runId(), value.userId(), value.applicationCode(), value.workflowJson(), value.applicationSystemPrompt(), value.command(), value.runKey(), value.frame(), value.modelInvocations(), value.reasoning(), value.toolResults(), value.usage(), status, value.updatedAt(), value.nativePhaseRunIds())); }
    }
}
