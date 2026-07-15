package online.yudream.base.domain.platform.ai.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Limits native AI tool callbacks to the tools explicitly granted by one Agent application run. */
public final class AiAgentToolExecutionScope implements AutoCloseable {

    private static final ThreadLocal<ScopeState> CURRENT = new ThreadLocal<>();

    private final ScopeState previous;
    private boolean closed;

    private AiAgentToolExecutionScope(Set<String> allowedToolNames) {
        previous = CURRENT.get();
        CURRENT.set(new ScopeState(null, immutableNames(allowedToolNames)));
    }

    private AiAgentToolExecutionScope(List<AiAgentTool> tools) {
        previous = CURRENT.get();
        List<AiAgentTool> scopedTools = tools == null ? List.of() : List.copyOf(tools);
        Set<String> allowedToolNames = new LinkedHashSet<>();
        scopedTools.forEach(tool -> allowedToolNames.add(tool.descriptor().name()));
        CURRENT.set(new ScopeState(scopedTools, Set.copyOf(allowedToolNames)));
    }

    public static AiAgentToolExecutionScope open(Set<String> allowedToolNames) {
        return new AiAgentToolExecutionScope(allowedToolNames);
    }

    public static AiAgentToolExecutionScope open(List<AiAgentTool> tools) {
        return new AiAgentToolExecutionScope(tools);
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

    private record ScopeState(List<AiAgentTool> tools, Set<String> allowedToolNames) {
    }
}
