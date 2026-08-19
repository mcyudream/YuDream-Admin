package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStepStatus;
import online.yudream.base.domain.platform.agent.event.AgentTraceEvent;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStep;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 执行链路追踪器：在工作流执行入口开启会话，装饰节点/思考/工具回调，
 * 执行结束后将完整追踪落库并广播增量事件。追踪自身的任何失败不得影响业务执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionTracer {

    private final AgentTraceProperties properties;
    private final AgentExecutionTraceRepo traceRepo;
    private final AgentRuntimeApplicationRegistry runtimeApplications;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AgentTraceSession begin(AgentApplication application, AgentRunCmd command, AgentTraceSource hint) {
        if (!properties.isEnabled()) {
            return AgentTraceSession.NOOP;
        }
        String ownerPluginCode = ownerPluginCode(application);
        AgentTraceSource source = hint != null
                ? hint
                : (ownerPluginCode != null ? AgentTraceSource.PLUGIN : AgentTraceSource.SYSTEM);
        AgentExecutionTrace trace = AgentExecutionTrace.start(
                UUID.randomUUID().toString().replace("-", ""),
                source,
                ownerPluginCode,
                application,
                truncate(command == null ? null : command.getInput(), properties.getMaxTextLength())
        );
        publish(AgentTraceEvent.started(trace));
        return new RecordingSession(trace);
    }

    private String ownerPluginCode(AgentApplication application) {
        if (application == null) {
            return null;
        }
        if (StringUtils.hasText(application.getSourcePluginCode())) {
            return application.getSourcePluginCode();
        }
        if (!StringUtils.hasText(application.getCode())) {
            return null;
        }
        try {
            return runtimeApplications.ownerCode(application.getCode()).orElse(null);
        } catch (RuntimeException e) {
            log.debug("反查插件 Agent 归属失败：{}", e.getMessage());
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String toolDetail(AiAgentToolResult tool) {
        String detail;
        try {
            detail = objectMapper.writeValueAsString(tool.payload() == null ? Map.of() : tool.payload());
        } catch (Exception e) {
            detail = String.valueOf(tool.payload());
        }
        return truncate(detail, properties.getMaxTextLength());
    }

    private void publish(AgentTraceEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.debug("发布 Agent 追踪事件失败：{}", e.getMessage());
        }
    }

    private void persist(AgentExecutionTrace trace) {
        try {
            traceRepo.save(trace);
        } catch (RuntimeException e) {
            log.warn("Agent 执行追踪落库失败：{}", e.getMessage());
        }
    }

    /** 记录中的节点执行轨迹，完成后转换为不可变步骤。 */
    private record OpenStep(
            int seq,
            String nodeId,
            String nodeKind,
            String nodeTitle,
            String inputSummary,
            StringBuilder reasoning,
            LocalDateTime startTime
    ) {
        AgentTraceStep toStep(AgentTraceStepStatus status, String outputSummary, String message, int maxReasoningLength) {
            LocalDateTime endTime = status == AgentTraceStepStatus.RUNNING ? null : LocalDateTime.now();
            return new AgentTraceStep(
                    seq, nodeId, nodeKind, nodeTitle, status,
                    inputSummary, outputSummary,
                    reasoning.isEmpty() ? null : truncate(reasoning, maxReasoningLength),
                    null, null, message,
                    startTime, endTime,
                    endTime == null ? null : Duration.between(startTime, endTime).toMillis()
            );
        }

        private static String truncate(StringBuilder buffer, int maxLength) {
            if (maxLength <= 0 || buffer.length() <= maxLength) {
                return buffer.toString();
            }
            return buffer.substring(0, maxLength) + "...";
        }
    }

    private final class RecordingSession implements AgentTraceSession {

        private final AgentExecutionTrace trace;
        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<String, OpenStep> openSteps = new LinkedHashMap<>();

        private RecordingSession(AgentExecutionTrace trace) {
            this.trace = trace;
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public String traceId() {
            return trace.getTraceId();
        }

        @Override
        public void nodeStarted(String nodeId, String nodeKind, String nodeTitle, String inputSummary) {
            OpenStep open = new OpenStep(
                    sequence.incrementAndGet(), nodeId, nodeKind, nodeTitle, inputSummary,
                    new StringBuilder(), LocalDateTime.now()
            );
            openSteps.put(nodeId, open);
            AgentTraceStep step = open.toStep(AgentTraceStepStatus.RUNNING, null, null, properties.getMaxReasoningLength());
            trace.getSteps().add(step);
            publish(AgentTraceEvent.appended(trace, step));
        }

        @Override
        public void nodeCompleted(String nodeId, String outputSummary) {
            finishNode(nodeId, AgentTraceStepStatus.COMPLETED, outputSummary, null);
        }

        @Override
        public void nodeFailed(String nodeId, String error) {
            finishNode(nodeId, AgentTraceStepStatus.FAILED, null, error == null ? "节点执行失败" : error);
        }

        private void finishNode(String nodeId, AgentTraceStepStatus status, String outputSummary, String message) {
            OpenStep open = openSteps.remove(nodeId);
            if (open == null) {
                return;
            }
            AgentTraceStep step = open.toStep(status, outputSummary, message, properties.getMaxReasoningLength());
            if (step.seq() <= trace.getSteps().size()) {
                trace.getSteps().set(step.seq() - 1, step);
            }
            publish(AgentTraceEvent.appended(trace, step));
        }

        @Override
        public void nodeSkipped(String nodeId, String nodeKind, String nodeTitle) {
            LocalDateTime now = LocalDateTime.now();
            AgentTraceStep step = new AgentTraceStep(
                    sequence.incrementAndGet(), nodeId, nodeKind, nodeTitle, AgentTraceStepStatus.SKIPPED,
                    null, null, null, null, null, "条件分支未命中",
                    now, now, 0L
            );
            trace.getSteps().add(step);
            publish(AgentTraceEvent.appended(trace, step));
        }

        @Override
        public void reasoningDelta(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            OpenStep current = currentOpenStep();
            if (current == null) {
                return;
            }
            if (current.reasoning().length() < properties.getMaxReasoningLength()) {
                current.reasoning().append(delta);
            }
        }

        private OpenStep currentOpenStep() {
            OpenStep current = null;
            for (OpenStep open : openSteps.values()) {
                current = open;
            }
            return current;
        }

        @Override
        public void toolResult(AiAgentToolResult tool) {
            if (tool == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            AgentTraceStep step = new AgentTraceStep(
                    sequence.incrementAndGet(), null, "tool", tool.toolName(), AgentTraceStepStatus.COMPLETED,
                    null, null, null,
                    tool.toolName(), toolDetail(tool), tool.message(),
                    now, now, 0L
            );
            trace.getSteps().add(step);
            publish(AgentTraceEvent.appended(trace, step));
        }

        @Override
        public void succeed(AgentWorkflowRuntimeResult result) {
            trace.succeed(
                    truncate(result == null ? null : result.content(), properties.getMaxTextLength()),
                    truncate(result == null ? null : result.reasoning(), properties.getMaxReasoningLength()),
                    result == null ? null : result.usage()
            );
            persist(trace);
            publish(AgentTraceEvent.completed(trace));
        }

        @Override
        public void fail(RuntimeException error) {
            trace.fail(error == null || error.getMessage() == null ? "Agent 执行失败" : error.getMessage());
            persist(trace);
            publish(AgentTraceEvent.failed(trace));
        }
    }
}
