package online.yudream.base.domain.platform.ai.service;

import java.util.Set;

/** Limits native AI tool callbacks to the tools explicitly granted by one Agent application run. */
public final class AiAgentToolExecutionScope implements AutoCloseable {

    private static final ThreadLocal<Set<String>> CURRENT = new ThreadLocal<>();

    private AiAgentToolExecutionScope(Set<String> allowedToolNames) {
        CURRENT.set(allowedToolNames == null ? Set.of() : Set.copyOf(allowedToolNames));
    }

    public static AiAgentToolExecutionScope open(Set<String> allowedToolNames) {
        return new AiAgentToolExecutionScope(allowedToolNames);
    }

    public static Set<String> current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
