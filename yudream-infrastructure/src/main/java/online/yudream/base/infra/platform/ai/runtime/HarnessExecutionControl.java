package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** 主动停止时关闭当前模型传输，使框架能立即到达协作检查点；不影响浏览器订阅。 */
final class HarnessExecutionControl implements MiddlewareBase {
    private final Sinks.Empty<Void> stop = Sinks.empty();
    private final AtomicBoolean interrupted = new AtomicBoolean();
    void interruptModel() { interrupted.set(true); stop.tryEmitEmpty(); }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                       Function<ModelCallInput, Flux<AgentEvent>> next) {
        return next.apply(input).takeUntilOther(stop.asMono())
                .concatWith(Flux.defer(() -> interrupted.get()
                        ? Mono.error(new InterruptedException("Agent execution interrupted")) : Mono.empty()));
    }
}
