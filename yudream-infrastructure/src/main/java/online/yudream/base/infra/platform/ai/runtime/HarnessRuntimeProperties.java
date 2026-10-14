package online.yudream.base.infra.platform.ai.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Harness 运行时配置（yudream.platform.agent.*）。
 */
@Data
@ConfigurationProperties(prefix = "yudream.platform.agent")
public class HarnessRuntimeProperties {

    /** 工作区模板根目录（AGENTS.md 等静态资产种子；运行时产物经 Remote filesystem 落 MongoDB） */
    private String workspaceDir = "config/agent-workspace";

    /** 对话上下文压缩：触发消息条数（0 关闭） */
    private int compactionTriggerMessages = 30;

    /** 对话上下文压缩：压缩后保留的尾部消息条数 */
    private int compactionKeepMessages = 10;

    /** 工作区上下文（MEMORY.md 等）注入预算，单位 token */
    private int maxContextTokens = 8000;

    /** 匿名用户（无 userId 调用）的工作区命名空间 */
    private String anonymousUserId = "_anon";

    /** 单轮最大迭代次数（含工具调用轮） */
    private int maxIters = 16;

    /** 是否注册系统级 AiAgentTool 工具到 Harness Agent */
    private boolean systemToolsEnabled = true;

    /** 是否启用双层长期记忆（日流水账 + MEMORY.md 每轮注入）；关闭可省去每轮后的提炼调用 */
    private boolean memoryHooksEnabled = true;

    /** 允许工作区 profile 委派子 Agent；默认关闭，需先配置真实执行边界。 */
    private boolean subagentsEnabled = false;
}
