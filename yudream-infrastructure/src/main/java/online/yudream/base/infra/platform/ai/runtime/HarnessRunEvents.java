package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.ChatUsage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.PendingToolConfirmation;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunReference;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** 原生 AgentSession 持有执行订阅；此中间件只观察真实事件并投影到统一应用出口。 */
final class HarnessRunEvents implements MiddlewareBase {
    private final Consumer<String> onDelta;
    private final Consumer<String> onReasoningDelta;
    private final Consumer<AiGenerationProgress> onProgress;
    private final Consumer<HarnessRunReference> onReference;
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<ChatUsage> usages = new ArrayList<>();
    private final Map<String, ChatUsage> modelUsages = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> modelCalls = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> usageKnownCalls = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> modelFinishedCalls = new java.util.LinkedHashSet<>();
    private Runnable onFirstModelStarted;
    private boolean modelMetering;
    private final List<PendingToolConfirmation> confirmations = new ArrayList<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean started;
    private boolean structuredToolDeltas;

    HarnessRunEvents(Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                     Consumer<AiGenerationProgress> onProgress) {
        this(onDelta, onReasoningDelta, onProgress, null);
    }

    HarnessRunEvents(Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                     Consumer<AiGenerationProgress> onProgress, Consumer<HarnessRunReference> onReference) {
        this.onDelta = onDelta;
        this.onReasoningDelta = onReasoningDelta;
        this.onProgress = onProgress;
        this.onReference = onReference;
    }

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                   Function<AgentInput, Flux<AgentEvent>> next) {
        started = true;
        if (onReference != null) {
            // AgentBase.getAgentId() 是每次 build 随机生成的实例 UUID，不能用于恢复寻址；
            // 会话日志与恢复路由都以 builder 的逻辑 name/agentId（即 agentCode）为准。
            onReference.accept(new HarnessRunReference(agent.getName(), context.getSessionId(),
                    context.get(io.agentscope.core.session.SessionRecorder.TURN_ID_KEY), context.getRunId()));
        }
        return next.apply(input).doOnNext(this::accept).doFinally(signal -> finished.countDown());
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext context, ReasoningInput input,
                                       Function<ReasoningInput, Flux<AgentEvent>> next) {
        progress("analysis", "正在请求模型并生成回答。");
        return next.apply(input);
    }

    private synchronized void accept(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta && hasText(delta.getDelta())) {
            content.append(delta.getDelta());
            if (onDelta != null) {
                onDelta.accept(delta.getDelta());
            }
        } else if (event instanceof ThinkingBlockDeltaEvent thinking && hasText(thinking.getDelta())) {
            reasoning.append(thinking.getDelta());
            if (onReasoningDelta != null) {
                onReasoningDelta.accept(thinking.getDelta());
            }
        } else if (structuredToolDeltas && event instanceof ToolCallDeltaEvent tool
                && io.agentscope.core.ReActAgent.STRUCTURED_OUTPUT_TOOL_NAME.equals(tool.getToolCallName())
                && hasText(tool.getDelta())) {
            // 兼容供应商使用框架合成工具输出 JSON；仅原样投影官方真实参数流。
            content.append(tool.getDelta());
            if (onDelta != null) onDelta.accept(tool.getDelta());
        } else if (!modelMetering && event instanceof ModelCallEndEvent end && end.getUsage() != null) {
            usages.add(end.getUsage());
        } else if (event instanceof RequireUserConfirmEvent confirm) {
            for (var call : confirm.getToolCalls()) {
                confirmations.add(new PendingToolConfirmation(call.getId(), call.getName(),
                        call.getInput() == null ? Map.of() : call.getInput(), "该工具需要用户确认后才能执行"));
            }
            progress("awaiting-approval", "正在等待工具执行确认。");
        } else if (event instanceof ModelCallStartEvent) {
            progress("analysis", "正在请求模型并生成回答。");
        } else if (event instanceof ToolResultStartEvent) {
            progress("tool-start", "正在执行工具。");
        } else if (event instanceof ToolResultEndEvent) {
            progress("tool-complete", "工具执行完成。");
        }
    }

    private void progress(String action, String value) {
        if (onProgress != null) {
            onProgress.accept(new AiGenerationProgress(action, value));
        }
    }

    void awaitObserver() throws InterruptedException {
        // run/end 日志先提交，随后框架完成订阅；避免 close 抢先中断最后的资源清理。
        if (started) {
            finished.await(10, TimeUnit.SECONDS);
        }
    }

    synchronized String content() { return content.toString(); }
    synchronized String reasoning() { return reasoning.toString(); }
    boolean started() { return started; }
    synchronized List<PendingToolConfirmation> confirmations() { return List.copyOf(confirmations); }

    synchronized AiUsage usage() {
        long input = 0;
        long output = 0;
        for (ChatUsage usage : modelMetering ? modelUsages.values() : usages) {
            input += usage.getInputTokens();
            output += usage.getOutputTokens();
        }
        return new AiUsage(input, output, input + output);
    }

    synchronized void recordUsage(ChatUsage usage) {
        if (!modelMetering && usage != null) {
            usages.add(usage);
        }
    }

    synchronized void useModelMetering() { modelMetering = true; }
    synchronized void structuredToolDeltas(boolean enabled) { structuredToolDeltas = enabled; }
    synchronized void onModelStarted(Runnable callback) { onFirstModelStarted = callback; }
    synchronized void modelStarted(String callId) {
        boolean first = modelCalls.isEmpty();
        modelCalls.add(callId);
        if (first && onFirstModelStarted != null) onFirstModelStarted.run();
    }
    synchronized boolean hasUnknownUsage() { return !usageKnownCalls.containsAll(modelCalls); }
    synchronized void recordModelUsage(String callId, ChatUsage usage) {
        if (modelFinishedCalls.contains(callId)) usageKnownCalls.add(callId);
        modelUsages.put(callId, usage);
    }
    synchronized void modelFinished(String callId) {
        modelFinishedCalls.add(callId);
        if (modelUsages.containsKey(callId)) usageKnownCalls.add(callId);
    }
    synchronized void modelCompleted(String callId) {
        if (modelUsages.containsKey(callId)) usageKnownCalls.add(callId);
    }

    private static boolean hasText(String value) { return value != null && !value.isEmpty(); }
}
