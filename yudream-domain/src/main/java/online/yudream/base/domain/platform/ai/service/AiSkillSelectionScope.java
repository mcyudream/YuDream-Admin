package online.yudream.base.domain.platform.ai.service;

import java.util.Set;

/**
 * 本次运行可用的技能名单（对话里由用户勾选）：Harness 装配 Agent 时在运行线程读取，
 * 把平台技能库收窄为名单内的已启用技能。未打开作用域表示不限制（全部已启用技能）。
 */
public final class AiSkillSelectionScope implements AutoCloseable {

    private static final ThreadLocal<Set<String>> CURRENT = new ThreadLocal<>();

    private final Set<String> previous;
    private final Set<String> installed;
    private boolean closed;

    private AiSkillSelectionScope(Set<String> names) {
        previous = CURRENT.get();
        installed = names == null ? Set.of() : Set.copyOf(names);
        CURRENT.set(installed);
    }

    public static AiSkillSelectionScope open(Set<String> names) {
        return new AiSkillSelectionScope(names);
    }

    /** 当前名单；null 表示不限制 */
    public static Set<String> current() {
        return CURRENT.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (CURRENT.get() != installed) {
            throw new IllegalStateException("技能选择作用域必须按后进先出顺序关闭");
        }
        closed = true;
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
