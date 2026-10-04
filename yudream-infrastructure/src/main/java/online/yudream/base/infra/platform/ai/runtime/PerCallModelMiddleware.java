package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 按次调用模型中间件：从 RuntimeContext 读取 {@link AgentModelSelection}，
 * 在 onModelCall 时机替换 agent 构建期绑定的默认模型与生成选项，
 * 使单个 HarnessAgent 实例可以按会话/请求选择不同供应商模型。
 */
public class PerCallModelMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext runtimeContext,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next
    ) {
        AgentModelSelection selection = runtimeContext == null ? null : runtimeContext.get(AgentModelSelection.class);
        if (selection == null || selection.model() == null) {
            return next.apply(input);
        }
        return next.apply(new ModelCallInput(
                input.messages(),
                input.tools(),
                selection.options() != null ? selection.options() : input.options(),
                selection.model()));
    }
}
