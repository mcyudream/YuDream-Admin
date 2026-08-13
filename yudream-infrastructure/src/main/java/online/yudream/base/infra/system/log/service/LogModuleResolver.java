package online.yudream.base.infra.system.log.service;

import java.util.List;
import java.util.Locale;

/**
 * 将 logger 名称解析为面向管理员的模块名，用于日志模块筛选。
 * 规则按顺序匹配，命中即返回；未命中回退为「系统」。
 */
public final class LogModuleResolver {

    private static final List<Rule> RULES = List.of(
            new Rule("milky", "Milky 消息平台"),
            new Rule("aichatbot", "AI 群聊机器人"),
            new Rule("ai-chatbot", "AI 群聊机器人"),
            new Rule("qqbotautomation", "QQ 群自动化"),
            new Rule("qqbot-automation", "QQ 群自动化"),
            new Rule("codextasknotify", "Codex 任务通知"),
            new Rule("codex-task-notify", "Codex 任务通知"),
            new Rule("system.monitor", "系统监控"),
            new Rule("system.user", "用户管理"),
            new Rule("system.menu", "菜单管理"),
            new Rule("system.security", "安全中心"),
            new Rule("system.log", "系统日志"),
            new Rule("system.setting", "系统设置"),
            new Rule("system.command", "指令管理"),
            new Rule("system.dashboard", "仪表盘"),
            new Rule("system.file", "文件管理"),
            new Rule("platform.capability", "能力管理"),
            new Rule("platform.plugin", "插件管理"),
            new Rule("platform.ai", "AI 平台"),
            new Rule("platform.agent", "Agent 平台"),
            new Rule("platform.cms", "内容定制"),
            new Rule("platform.wiki", "Wiki 知识库"),
            new Rule("platform.form", "动态表单"),
            new Rule("platform.integration", "集成调用"),
            new Rule("platform.document", "Word 模板"),
            new Rule("platform.graph", "图数据库"),
            new Rule("springframework", "Spring"),
            new Rule("reactor", "Reactor"),
            new Rule("io.netty", "Netty"),
            new Rule("mongodb", "MongoDB"),
            new Rule("lettuce", "Redis"),
            new Rule("org.apache", "Apache")
    );

    public String resolve(String loggerName) {
        String logger = loggerName == null ? "" : loggerName.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (logger.contains(rule.keyword())) {
                return rule.module();
            }
        }
        return "系统";
    }

    public List<String> knownModules() {
        return RULES.stream().map(Rule::module).distinct().sorted().toList();
    }

    private record Rule(String keyword, String module) {
    }
}
