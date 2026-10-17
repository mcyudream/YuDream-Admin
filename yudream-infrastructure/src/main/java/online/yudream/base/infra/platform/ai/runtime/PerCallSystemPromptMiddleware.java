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
 * <p>
 * 系统工具按模块提供时追加模块加载协议说明：先看目录，再按需用 load_tools 加载模块。
 */
public class PerCallSystemPromptMiddleware implements MiddlewareBase {

    private static final String TOOL_MODULE_HINT = """

            ## 系统工具模块
            系统工具按模块提供，当前默认只提供常驻模块的工具。
            需要其他模块（例如站点画布 cms、MCP 服务 mcp:xxx）时：先调用 load_tools 查看模块目录，
            再调用 load_tools 并传入对应 module 编码；加载成功后该模块的工具即可直接调用。
            不要臆造或猜测未加载工具的名称。
            """;

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext runtimeContext, String systemPrompt) {
        AgentSystemPromptOverride override = runtimeContext == null
                ? null
                : runtimeContext.get(AgentSystemPromptOverride.class);
        String prompt = override != null && override.present()
                ? override.prompt()
                : (systemPrompt == null ? "" : systemPrompt);
        online.yudream.base.domain.platform.ai.valobj.AiToolLoadState loadState = runtimeContext == null
                ? null
                : runtimeContext.get(online.yudream.base.domain.platform.ai.valobj.AiToolLoadState.class);
        if (loadState != null && loadState.onDemand()) {
            return Mono.just(prompt + TOOL_MODULE_HINT);
        }
        return Mono.just(prompt);
    }
}
