package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceEventAction;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStepStatus;
import online.yudream.base.domain.platform.agent.event.AgentTraceEvent;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionTracerTest {

    @Test
    void recordsNodeLifecycleReasoningAndToolStepsThenPersistsOnSuccess() {
        InMemoryTraceRepo repo = new InMemoryTraceRepo();
        List<AgentTraceEvent> events = new ArrayList<>();
        AgentExecutionTracer tracer = tracer(repo, events, mock(AgentRuntimeApplicationRegistry.class));
        AgentApplication application = AgentApplication.builder().name("问答").code("qa").build();
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("你好");

        AgentTraceSession session = tracer.begin(application, command, null);
        assertThat(session.active()).isTrue();
        assertThat(session.traceId()).isNotBlank();

        session.nodeStarted("start", "start", "开始", "你好");
        session.nodeCompleted("start", "你好");
        session.nodeStarted("llm", "llm", "模型", "你好");
        session.reasoningDelta("用户在");
        session.reasoningDelta("打招呼");
        session.toolResult(new AiAgentToolResult("wiki.search", "call", null, "检索完成", Map.of("hits", 2)));
        session.nodeCompleted("llm", "你好！有什么可以帮你？");
        session.succeed(new AgentWorkflowRuntimeResult("你好！有什么可以帮你？", "用户在打招呼", List.of(), new AiUsage(3, 5, 8)));

        assertThat(repo.saved).hasSize(1);
        AgentExecutionTrace trace = repo.saved.getFirst();
        assertThat(trace.getStatus()).isEqualTo(AgentTraceStatus.SUCCEEDED);
        assertThat(trace.getSource()).isEqualTo(AgentTraceSource.SYSTEM);
        assertThat(trace.getOwnerPluginCode()).isNull();
        assertThat(trace.getAgentCode()).isEqualTo("qa");
        assertThat(trace.getInput()).isEqualTo("你好");
        assertThat(trace.getFinalOutput()).isEqualTo("你好！有什么可以帮你？");
        assertThat(trace.getReasoning()).isEqualTo("用户在打招呼");
        assertThat(trace.getUsage().totalTokens()).isEqualTo(8);
        assertThat(trace.getDurationMs()).isNotNull();

        assertThat(trace.getSteps()).hasSize(3);
        AgentTraceStep start = trace.getSteps().get(0);
        assertThat(start.status()).isEqualTo(AgentTraceStepStatus.COMPLETED);
        assertThat(start.inputSummary()).isEqualTo("你好");
        assertThat(start.outputSummary()).isEqualTo("你好");
        assertThat(start.durationMs()).isNotNull();
        AgentTraceStep llm = trace.getSteps().get(1);
        assertThat(llm.reasoning()).isEqualTo("用户在打招呼");
        assertThat(llm.outputSummary()).isEqualTo("你好！有什么可以帮你？");
        AgentTraceStep tool = trace.getSteps().get(2);
        assertThat(tool.nodeKind()).isEqualTo("tool");
        assertThat(tool.toolName()).isEqualTo("wiki.search");
        assertThat(tool.toolDetail()).contains("\"hits\":2");

        assertThat(events).extracting(AgentTraceEvent::action)
                .containsExactly(
                        AgentTraceEventAction.STARTED,
                        AgentTraceEventAction.STEP,
                        AgentTraceEventAction.STEP,
                        AgentTraceEventAction.STEP,
                        AgentTraceEventAction.STEP,
                        AgentTraceEventAction.STEP,
                        AgentTraceEventAction.COMPLETED
                );
        assertThat(events).allMatch(event -> event.traceId().equals(session.traceId()));
    }

    @Test
    void failurePersistsFailedTraceWithError() {
        InMemoryTraceRepo repo = new InMemoryTraceRepo();
        List<AgentTraceEvent> events = new ArrayList<>();
        AgentExecutionTracer tracer = tracer(repo, events, mock(AgentRuntimeApplicationRegistry.class));

        AgentTraceSession session = tracer.begin(AgentApplication.builder().name("调试").code("debug-app").build(), new AgentRunCmd(), AgentTraceSource.DEBUG);
        session.nodeStarted("llm", "llm", "模型", null);
        session.nodeFailed("llm", "模型超时");
        session.fail(new RuntimeException("工作流中断"));

        assertThat(repo.saved).hasSize(1);
        AgentExecutionTrace trace = repo.saved.getFirst();
        assertThat(trace.getSource()).isEqualTo(AgentTraceSource.DEBUG);
        assertThat(trace.getStatus()).isEqualTo(AgentTraceStatus.FAILED);
        assertThat(trace.getError()).isEqualTo("工作流中断");
        assertThat(trace.getSteps()).hasSize(1);
        assertThat(trace.getSteps().getFirst().status()).isEqualTo(AgentTraceStepStatus.FAILED);
        assertThat(trace.getSteps().getFirst().message()).isEqualTo("模型超时");
        assertThat(events).extracting(AgentTraceEvent::action)
                .endsWith(AgentTraceEventAction.FAILED);
    }

    @Test
    void pluginOwnedAgentInfersPluginSourceAndOwner() {
        InMemoryTraceRepo repo = new InMemoryTraceRepo();
        List<AgentTraceEvent> events = new ArrayList<>();
        AgentRuntimeApplicationRegistry registry = mock(AgentRuntimeApplicationRegistry.class);
        when(registry.ownerCode("qa-bot")).thenReturn(Optional.of("qq-bot"));
        AgentExecutionTracer tracer = tracer(repo, events, registry);
        AgentApplication application = AgentApplication.builder().name("QQ 机器人").code("qa-bot").build();

        AgentTraceSession session = tracer.begin(application, new AgentRunCmd(), null);
        session.succeed(new AgentWorkflowRuntimeResult("ok", List.of()));

        AgentExecutionTrace trace = repo.saved.getFirst();
        assertThat(trace.getSource()).isEqualTo(AgentTraceSource.PLUGIN);
        assertThat(trace.getOwnerPluginCode()).isEqualTo("qq-bot");
    }

    @Test
    void longInputIsTruncated() {
        InMemoryTraceRepo repo = new InMemoryTraceRepo();
        AgentTraceProperties properties = new AgentTraceProperties();
        properties.setMaxTextLength(10);
        AgentExecutionTracer tracer = new AgentExecutionTracer(
                properties, repo, mock(AgentRuntimeApplicationRegistry.class), event -> {
        }, new ObjectMapper());
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("一二三四五六七八九十百千万亿");

        AgentTraceSession session = tracer.begin(AgentApplication.builder().name("截断").code("truncate").build(), command, null);
        session.succeed(new AgentWorkflowRuntimeResult("ok", List.of()));

        assertThat(repo.saved.getFirst().getInput()).hasSize(13).endsWith("...");
    }

    @Test
    void disabledTracerReturnsNoopSessionWithoutSideEffects() {
        AgentExecutionTraceRepo repo = mock(AgentExecutionTraceRepo.class);
        AgentTraceProperties properties = new AgentTraceProperties();
        properties.setEnabled(false);
        AgentExecutionTracer tracer = new AgentExecutionTracer(
                properties, repo, mock(AgentRuntimeApplicationRegistry.class), event -> {
        }, new ObjectMapper());

        AgentTraceSession session = tracer.begin(AgentApplication.builder().name("关闭").code("off").build(), new AgentRunCmd(), null);

        assertThat(session.active()).isFalse();
        session.nodeStarted("n", "llm", "模型", null);
        session.succeed(new AgentWorkflowRuntimeResult("ok", List.of()));
        verifyNoInteractions(repo);
    }

    private AgentExecutionTracer tracer(InMemoryTraceRepo repo, List<AgentTraceEvent> events, AgentRuntimeApplicationRegistry registry) {
        return new AgentExecutionTracer(new AgentTraceProperties(), repo, registry, event -> {
            if (event instanceof AgentTraceEvent traceEvent) {
                events.add(traceEvent);
            }
        }, new ObjectMapper());
    }

    private static final class InMemoryTraceRepo implements AgentExecutionTraceRepo {
        private final List<AgentExecutionTrace> saved = new ArrayList<>();

        @Override
        public AgentExecutionTrace save(AgentExecutionTrace trace) {
            saved.add(trace);
            return trace;
        }

        @Override
        public Optional<AgentExecutionTrace> findByTraceId(String traceId) {
            return saved.stream().filter(trace -> trace.getTraceId().equals(traceId)).findFirst();
        }

        @Override
        public List<AgentExecutionTrace> query(AgentTraceQuery query) {
            return List.of();
        }

        @Override
        public long count(AgentTraceQuery query) {
            return 0;
        }
    }
}
