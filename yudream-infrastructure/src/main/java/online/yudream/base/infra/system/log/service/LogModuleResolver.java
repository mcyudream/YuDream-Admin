package online.yudream.base.infra.system.log.service;

import online.yudream.base.domain.platform.milky.model.OfficialQqBotEventCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 logger 名称解析为面向管理员的模块名，用于日志模块筛选。
 * 规则按顺序匹配，命中即返回；未命中回退为「系统」。
 * 官方 QQ 入站事件通过 MDC logModule 直接落到事件分类模块。
 */
public final class LogModuleResolver {

    public static final String MDC_MODULE = "logModule";

    private static final Set<String> QQ_EVENT_MODULES = new LinkedHashSet<>(OfficialQqBotEventCatalog.LOG_MODULES);

    private static final List<Rule> RULES = List.of(
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
            new Rule("platform.mail", "入站邮箱"),
            new Rule("inbound-mail", "入站邮箱"),
            new Rule("platform.milky", "QQ 消息平台"),
            new Rule(".milky.", "QQ 消息平台"),
            new Rule("springframework", "Spring"),
            new Rule("reactor", "Reactor"),
            new Rule("io.netty", "Netty"),
            new Rule("mongodb", "MongoDB"),
            new Rule("lettuce", "Redis"),
            new Rule("org.apache", "Apache")
    );

    public String resolve(String loggerName) {
        return resolve(loggerName, Map.of());
    }

    public String resolve(String loggerName, Map<String, String> mdc) {
        String override = mdcValue(mdc, MDC_MODULE);
        if (override != null) {
            return override;
        }
        String logger = loggerName == null ? "" : loggerName.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (logger.contains(rule.keyword())) {
                return rule.module();
            }
        }
        return "系统";
    }

    public List<String> knownModules() {
        LinkedHashSet<String> modules = new LinkedHashSet<>(OfficialQqBotEventCatalog.LOG_MODULES);
        RULES.stream().map(Rule::module).forEach(modules::add);
        return new ArrayList<>(modules);
    }

    public List<ModuleGroup> knownModuleGroups() {
        List<ModuleGroup> groups = new ArrayList<>();
        groups.add(new ModuleGroup("QQ 机器人", new ArrayList<>(QQ_EVENT_MODULES)));
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        buckets.put("系统", new ArrayList<>());
        buckets.put("平台", new ArrayList<>());
        buckets.put("插件", new ArrayList<>());
        buckets.put("基础设施", new ArrayList<>());
        for (String module : RULES.stream().map(Rule::module).distinct().toList()) {
            if (QQ_EVENT_MODULES.contains(module)) {
                continue;
            }
            buckets.get(bucketOf(module)).add(module);
        }
        buckets.forEach((label, names) -> {
            if (!names.isEmpty()) {
                groups.add(new ModuleGroup(label, names));
            }
        });
        return groups;
    }

    private static String bucketOf(String module) {
        if (module.startsWith("系统") || module.equals("用户管理") || module.equals("菜单管理")
                || module.equals("安全中心") || module.equals("指令管理") || module.equals("仪表盘")
                || module.equals("文件管理")) {
            return "系统";
        }
        if (module.contains("平台") || module.equals("能力管理") || module.equals("插件管理")
                || module.equals("内容定制") || module.equals("Wiki 知识库") || module.equals("动态表单")
                || module.equals("集成调用") || module.equals("Word 模板") || module.equals("图数据库")
                || module.equals("入站邮箱")
                || module.equals("AI 群聊机器人") || module.equals("QQ 群自动化") || module.equals("Codex 任务通知")) {
            return "平台";
        }
        if (module.equals("Spring") || module.equals("Reactor") || module.equals("Netty")
                || module.equals("MongoDB") || module.equals("Redis") || module.equals("Apache")) {
            return "基础设施";
        }
        return "插件";
    }

    private static String mdcValue(Map<String, String> mdc, String key) {
        if (mdc == null) {
            return null;
        }
        String value = mdc.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ModuleGroup(String label, List<String> modules) {
        public ModuleGroup {
            label = label == null ? "" : label;
            modules = modules == null ? List.of() : List.copyOf(modules);
        }
    }

    private record Rule(String keyword, String module) {
    }
}
