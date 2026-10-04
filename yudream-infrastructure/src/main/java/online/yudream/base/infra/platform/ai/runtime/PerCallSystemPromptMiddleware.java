package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Mono;

/**
 * 按次调用系统提示词中间件：在 onSystemPrompt 链最前端把构建期 sysPrompt 替换为
 * 本次运行覆盖值；工作区上下文（AGENTS.md/MEMORY.md）由框架内置的
 * WorkspaceContextMiddleware 在其后追加，不受影响。
 */
public class PerCallSystemPromptMiddleware implements MiddlewareBase {

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext runtimeContext, String systemPrompt) {
        AgentSystemPromptOverride override = runtimeContext == null
                ? null
                : runtimeContext.get(AgentSystemPromptOverride.class);
        if (override != null && override.present()) {
            return Mono.just(override.prompt());
        }
        return Mono.just(systemPrompt == null ? "" : systemPrompt);
    }
}
