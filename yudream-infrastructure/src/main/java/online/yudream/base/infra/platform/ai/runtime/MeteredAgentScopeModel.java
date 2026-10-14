package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/** 只记录框架已解析的用量，包含 Harness 压缩/记忆辅助调用；格式化、连接和推理仍由框架处理。 */
final class MeteredAgentScopeModel implements Model {
    private final Model delegate;
    private final HarnessRunEvents events;

    MeteredAgentScopeModel(Model delegate, HarnessRunEvents events) {
        this.delegate = delegate;
        this.events = events;
        events.useModelMetering();
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            String callId = UUID.randomUUID().toString();
            events.modelStarted(callId);
            return delegate.stream(messages, tools, options).doOnNext(response -> {
                if (response.getFinishReason() != null && !response.getFinishReason().isBlank()) events.modelFinished(callId);
                if (response.getUsage() != null && (response.getUsage().getInputTokens() > 0
                        || response.getUsage().getOutputTokens() > 0 || response.getUsage().getTotalTokens() > 0
                        || response.getFinishReason() != null)) {
                    events.recordModelUsage(callId, response.getUsage());
                }
            }).doOnComplete(() -> events.modelCompleted(callId));
        });
    }

    @Override public String getModelName() { return delegate.getModelName(); }
    @Override public int getContextWindowSize() { return delegate.getContextWindowSize(); }
    @Override public boolean supportsNativeStructuredOutput() { return delegate.supportsNativeStructuredOutput(); }
    @Override public boolean supportsNativeStructuredOutputWithTools() { return delegate.supportsNativeStructuredOutputWithTools(); }
}
