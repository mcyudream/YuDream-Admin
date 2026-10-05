package online.yudream.base.domain.platform.ai.valobj;

/**
 * 平台工具名 → 模型侧安全名的唯一换算点：模型可见工具名只保留
 * 字母/数字/下划线/连字符；平台侧关联仍用原始 descriptor.name()。
 */
public final class AgentToolNames {

    private AgentToolNames() {
    }

    public static String safeName(String name) {
        return name == null || name.isBlank() ? "tool" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
