package online.yudream.base.domain.platform.ai.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Limits native AI tool callbacks to the tools explicitly granted by one Agent application run.
 * <p>
 * 作用域同时带上「本次运行的发起人」：工具在运行线程里执行，拿不到请求级安全上下文，
 * 需要实例级授权的工具（如 {@code wiki.search}）只能靠这里带进来的用户 ID 判定主体；
 * 未显式给出时继承外层作用域的发起人，未登录的运行保持 null 并由工具侧 fail closed。
 */
public final class AiAgentToolExecutionScope implements AutoCloseable {

    private static final ThreadLocal<ScopeState> CURRENT = new ThreadLocal<>();

    private final ScopeState previous;
    private final ScopeState installed;
    private boolean closed;

    private AiAgentToolExecutionScope(Set<String> allowedToolNames, Long actingUserId) {
        previous = CURRENT.get();
        installed = new ScopeState(null, immutableNames(allowedToolNames), resolveActingUserId(actingUserId, previous));
        CURRENT.set(installed);
    }

    private AiAgentToolExecutionScope(List<AiAgentTool> tools, Long actingUserId) {
        previous = CURRENT.get();
        List<AiAgentTool> scopedTools = tools == null ? List.of() : List.copyOf(tools);
        Set<String> allowedToolNames = new LinkedHashSet<>();
        scopedTools.forEach(tool -> allowedToolNames.add(tool.descriptor().name()));
        installed = new ScopeState(scopedTools, Set.copyOf(allowedToolNames), resolveActingUserId(actingUserId, previous));
        CURRENT.set(installed);
    }

    public static AiAgentToolExecutionScope open(Set<String> allowedToolNames) {
        return new AiAgentToolExecutionScope(allowedToolNames, null);
    }

    public static AiAgentToolExecutionScope open(List<AiAgentTool> tools) {
        return new AiAgentToolExecutionScope(tools, null);
    }

    public static AiAgentToolExecutionScope open(Set<String> allowedToolNames, Long actingUserId) {
        return new AiAgentToolExecutionScope(allowedToolNames, actingUserId);
    }

    public static AiAgentToolExecutionScope open(List<AiAgentTool> tools, Long actingUserId) {
        return new AiAgentToolExecutionScope(tools, actingUserId);
    }

    /** 本次运行的发起人；无作用域或未登录时为 null。 */
    public static Long currentUserId() {
        ScopeState state = CURRENT.get();
        return state == null ? null : state.actingUserId();
    }

    private static Long resolveActingUserId(Long declared, ScopeState previous) {
        if (declared != null) {
            return declared;
        }
        return previous == null ? null : previous.actingUserId();
    }

    public static Set<String> current() {
        ScopeState state = CURRENT.get();
        return state == null ? null : state.allowedToolNames();
    }

    public static List<AiAgentTool> currentTools() {
        ScopeState state = CURRENT.get();
        return state == null ? null : state.tools();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (CURRENT.get() != installed) {
            throw new IllegalStateException("AI 工具执行作用域必须按后进先出顺序关闭");
        }
        closed = true;
        if (previous == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(previous);
    }

    private static Set<String> immutableNames(Set<String> allowedToolNames) {
        return allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames);
    }

    private record ScopeState(List<AiAgentTool> tools, Set<String> allowedToolNames, Long actingUserId) {
    }
}
