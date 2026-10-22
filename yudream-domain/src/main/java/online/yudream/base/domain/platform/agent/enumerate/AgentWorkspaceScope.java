package online.yudream.base.domain.platform.agent.enumerate;

/**
 * Agent 应用使用用户工作区的范围：SHARED 可见用户整个工作区；SCOPED 只见 apps/&lt;应用编码&gt;/ 子目录。
 * 每个用户仍只有一个工作区，范围只限制该应用的 AI 工具视野。
 */
public enum AgentWorkspaceScope {
    SHARED,
    SCOPED;

    public static AgentWorkspaceScope of(String value) {
        return "SCOPED".equalsIgnoreCase(value == null ? "" : value.trim()) ? SCOPED : SHARED;
    }

    /** 该应用在工作区中的根目录（相对路径，SHARED 为根） */
    public String root(String applicationCode) {
        return this == SCOPED ? "apps/" + applicationCode : "";
    }
}
