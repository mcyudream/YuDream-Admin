package online.yudream.base.infra.platform.menu.enumerate;

import online.yudream.base.domain.system.menu.anno.MenuModule;
import online.yudream.base.domain.system.menu.anno.MenuNode;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

@MenuModule(code = "platform", name = "平台能力", icon = "i-ri:node-tree", sort = 2)
public enum PlatformMenuModule {

    @MenuNode(code = "platform:wiki", name = "Wiki 知识库", type = MenuNodeType.MENU,
            path = "/platform/wiki", component = "platform/wiki/index.vue",
            icon = "i-ri:book-open-line", sort = 58)
    WIKI,

    @MenuNode(code = "platform:wiki:view", name = "查看 Wiki 知识库", type = MenuNodeType.BUTTON,
            parentName = "WIKI", permission = "platform:wiki:view")
    WIKI_VIEW,

    @MenuNode(code = "platform:wiki:edit", name = "编辑 Wiki 知识库", type = MenuNodeType.BUTTON,
            parentName = "WIKI", permission = "platform:wiki:edit")
    WIKI_EDIT,

    @MenuNode(code = "platform:wiki:publish", name = "发布 Wiki 页面", type = MenuNodeType.BUTTON,
            parentName = "WIKI", permission = "platform:wiki:publish")
    WIKI_PUBLISH,

    @MenuNode(code = "platform:wiki:delete", name = "删除 Wiki 内容", type = MenuNodeType.BUTTON,
            parentName = "WIKI", permission = "platform:wiki:delete")
    WIKI_DELETE,

    @MenuNode(code = "platform:wiki:manage", name = "管理 Wiki 检索配置", type = MenuNodeType.BUTTON,
            parentName = "WIKI", permission = "platform:wiki:manage")
    WIKI_MANAGE,

    @MenuNode(code = "platform:inbound-mail", name = "入站邮箱", type = MenuNodeType.MENU,
            path = "/platform/inbound-mail", component = "platform/inbound-mail/index.vue",
            icon = "i-ri:mail-download-line", sort = 77)
    INBOUND_MAIL,

    @MenuNode(code = "platform:inbound-mail:view", name = "查看入站邮件", type = MenuNodeType.BUTTON,
            parentName = "INBOUND_MAIL", permission = "platform:inbound-mail:view")
    INBOUND_MAIL_VIEW,

    @MenuNode(code = "platform:milky", name = "QQ 消息平台", type = MenuNodeType.MENU,
            path = "/platform/milky", component = "platform/milky/index.vue",
            icon = "i-ri:chat-3-line", sort = 76)
    MILKY,

    @MenuNode(code = "platform:milky:view", name = "查看 QQ 消息平台", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:milky:view")
    MILKY_VIEW,

    @MenuNode(code = "platform:milky:config", name = "配置 QQ 消息连接", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:milky:config")
    MILKY_CONFIG,

    @MenuNode(code = "platform:milky:connect", name = "连接 QQ 消息平台", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:milky:connect")
    MILKY_CONNECT,

    @MenuNode(code = "platform:milky:send", name = "发送 QQ 消息", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:milky:send")
    MILKY_SEND,

    @MenuNode(code = "platform:milky:internal", name = "调用 QQ 原生接口", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:milky:internal")
    MILKY_INTERNAL,

    @MenuNode(code = "platform:render:use", name = "渲染消息图片", type = MenuNodeType.BUTTON,
            parentName = "MILKY", permission = "platform:render:use")
    RENDER_USE,

    @MenuNode(code = "platform:capability", name = "能力管理", type = MenuNodeType.MENU,
            path = "/platform/capability", component = "platform/capability/index.vue",
            icon = "i-ri:dashboard-horizontal-line", sort = 100)
    CAPABILITY,

    @MenuNode(code = "platform:capability:view", name = "查看能力管理", type = MenuNodeType.BUTTON,
            parentName = "CAPABILITY", permission = "platform:capability:view")
    CAPABILITY_VIEW,

    @MenuNode(code = "platform:capability:config", name = "配置平台能力", type = MenuNodeType.BUTTON,
            parentName = "CAPABILITY", permission = "platform:capability:config")
    CAPABILITY_CONFIG,

    @MenuNode(code = "platform:capability:enable", name = "启用平台能力", type = MenuNodeType.BUTTON,
            parentName = "CAPABILITY", permission = "platform:capability:enable")
    CAPABILITY_ENABLE,

    @MenuNode(code = "platform:capability:disable", name = "禁用平台能力", type = MenuNodeType.BUTTON,
            parentName = "CAPABILITY", permission = "platform:capability:disable")
    CAPABILITY_DISABLE,

    @MenuNode(code = "platform:capability:test", name = "测试平台能力", type = MenuNodeType.BUTTON,
            parentName = "CAPABILITY", permission = "platform:capability:test")
    CAPABILITY_TEST,

    @MenuNode(code = "platform:plugin", name = "插件管理", type = MenuNodeType.MENU,
            path = "/platform/plugin", component = "platform/plugin/index.vue",
            icon = "i-ri:puzzle-2-line", sort = 95)
    PLUGIN,

    @MenuNode(code = "platform:plugin:view", name = "查看插件管理", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN", permission = "platform:plugin:view")
    PLUGIN_VIEW,

    @MenuNode(code = "platform:plugin:manage", name = "管理插件", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN", permission = "platform:plugin:manage")
    PLUGIN_MANAGE,

    @MenuNode(code = "platform:plugin-marketplace", name = "插件市场", type = MenuNodeType.MENU,
            path = "/platform/plugin-marketplace", component = "platform/plugin-marketplace/index.vue",
            icon = "i-ri:store-2-line", sort = 94)
    PLUGIN_MARKETPLACE,

    @MenuNode(code = "platform:plugin-marketplace:view", name = "查看插件市场", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKETPLACE", permission = "platform:plugin-marketplace:view")
    PLUGIN_MARKETPLACE_VIEW,

    @MenuNode(code = "platform:plugin-market-source", name = "市场源管理", type = MenuNodeType.MENU,
            path = "/platform/plugin-market-source", component = "platform/plugin-market-source/index.vue",
            icon = "i-ri:git-repository-line", sort = 93)
    PLUGIN_MARKET_SOURCE,

    @MenuNode(code = "platform:plugin-market-source:view", name = "查看市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:view")
    PLUGIN_MARKET_SOURCE_VIEW,

    @MenuNode(code = "platform:plugin-market-source:create", name = "添加市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:create")
    PLUGIN_MARKET_SOURCE_CREATE,

    @MenuNode(code = "platform:plugin-market-source:edit", name = "编辑市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:edit")
    PLUGIN_MARKET_SOURCE_EDIT,

    @MenuNode(code = "platform:plugin-market-source:delete", name = "删除市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:delete")
    PLUGIN_MARKET_SOURCE_DELETE,

    @MenuNode(code = "platform:plugin-market-source:run", name = "同步市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:run")
    PLUGIN_MARKET_SOURCE_RUN,

    @MenuNode(code = "platform:plugin-market-source:upload", name = "发布插件到市场源", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:upload")
    PLUGIN_MARKET_SOURCE_UPLOAD,

    @MenuNode(code = "platform:plugin-market-source:accept", name = "审核市场发布物", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_MARKET_SOURCE", permission = "platform:plugin-market-source:accept")
    PLUGIN_MARKET_SOURCE_ACCEPT,

    @MenuNode(code = "platform:plugin-publish", name = "插件发布", type = MenuNodeType.MENU,
            path = "/platform/plugin-publish", component = "platform/plugin-publish/index.vue",
            icon = "i-ri:upload-2-line", sort = 92, permission = "platform:plugin-market-source:upload")
    PLUGIN_PUBLISH,

    @MenuNode(code = "platform:plugin-market-source:publish", name = "跳过审核直接发布", type = MenuNodeType.BUTTON,
            parentName = "PLUGIN_PUBLISH", permission = "platform:plugin-market-source:publish")
    PLUGIN_PUBLISH_SKIP_REVIEW,

    @MenuNode(code = "platform:plugin-review", name = "发布审核", type = MenuNodeType.MENU,
            path = "/platform/plugin-review", component = "platform/plugin-review/index.vue",
            icon = "i-ri:shield-check-line", sort = 91, permission = "platform:plugin-market-source:accept")
    PLUGIN_REVIEW,

    @MenuNode(code = "platform:docs", name = "API 文档", type = MenuNodeType.MENU,
            path = "/platform/api-doc", component = "platform/api-doc/index.vue",
            icon = "i-ri:file-list-2-line", sort = 90)
    API_DOCS,

    @MenuNode(code = "platform:docs:view", name = "查看 API 文档", type = MenuNodeType.BUTTON,
            parentName = "API_DOCS", permission = "platform:docs:view")
    API_DOCS_VIEW,

    @MenuNode(code = "platform:docs:config", name = "配置 API 文档", type = MenuNodeType.BUTTON,
            parentName = "API_DOCS", permission = "platform:docs:config")
    API_DOCS_CONFIG,

    @MenuNode(code = "platform:integration", name = "集成调用", type = MenuNodeType.MENU,
            path = "/platform/integration", component = "platform/integration/index.vue",
            icon = "i-ri:terminal-box-line", sort = 80)
    INTEGRATION,

    @MenuNode(code = "platform:integration:view", name = "查看集成调用", type = MenuNodeType.BUTTON,
            parentName = "INTEGRATION", permission = "platform:integration:view")
    INTEGRATION_VIEW,

    @MenuNode(code = "platform:integration:edit", name = "编辑集成调用", type = MenuNodeType.BUTTON,
            parentName = "INTEGRATION", permission = "platform:integration:edit")
    INTEGRATION_EDIT,

    @MenuNode(code = "platform:integration:invoke", name = "执行 HTTP 调用", type = MenuNodeType.BUTTON,
            parentName = "INTEGRATION", permission = "platform:integration:invoke")
    INTEGRATION_INVOKE,

    @MenuNode(code = "platform:integration:execute", name = "执行运行脚本", type = MenuNodeType.BUTTON,
            parentName = "INTEGRATION", permission = "platform:integration:execute")
    INTEGRATION_EXECUTE,

    @MenuNode(code = "platform:integration:log:view", name = "查看集成日志", type = MenuNodeType.BUTTON,
            parentName = "INTEGRATION", permission = "platform:integration:log:view")
    INTEGRATION_LOG_VIEW,

    @MenuNode(code = "platform:document", name = "Word 模板", type = MenuNodeType.MENU,
            path = "/platform/document", component = "platform/document/index.vue",
            icon = "i-ri:file-word-2-line", sort = 70)
    DOCUMENT,

    @MenuNode(code = "platform:document:view", name = "查看 Word 模板", type = MenuNodeType.BUTTON,
            parentName = "DOCUMENT", permission = "platform:document:view")
    DOCUMENT_VIEW,

    @MenuNode(code = "platform:document:edit", name = "编辑 Word 模板", type = MenuNodeType.BUTTON,
            parentName = "DOCUMENT", permission = "platform:document:edit")
    DOCUMENT_EDIT,

    @MenuNode(code = "platform:document:generate", name = "生成 Word 文档", type = MenuNodeType.BUTTON,
            parentName = "DOCUMENT", permission = "platform:document:generate")
    DOCUMENT_GENERATE,

    @MenuNode(code = "platform:document:log:view", name = "查看 Word 生成记录", type = MenuNodeType.BUTTON,
            parentName = "DOCUMENT", permission = "platform:document:log:view")
    DOCUMENT_LOG_VIEW,

    @MenuNode(code = "platform:graph", name = "图数据库", type = MenuNodeType.MENU,
            path = "/platform/graph", component = "platform/graph/index.vue",
            icon = "i-ri:share-circle-line", sort = 65)
    GRAPH,

    @MenuNode(code = "platform:graph:view", name = "查看图数据库", type = MenuNodeType.BUTTON,
            parentName = "GRAPH", permission = "platform:graph:view")
    GRAPH_VIEW,

    @MenuNode(code = "platform:graph:edit", name = "编辑图数据库", type = MenuNodeType.BUTTON,
            parentName = "GRAPH", permission = "platform:graph:edit")
    GRAPH_EDIT,

    @MenuNode(code = "platform:graph:query", name = "执行图数据库查询", type = MenuNodeType.BUTTON,
            parentName = "GRAPH", permission = "platform:graph:query")
    GRAPH_QUERY,

    @MenuNode(code = "platform:graph:log:view", name = "查看图数据库日志", type = MenuNodeType.BUTTON,
            parentName = "GRAPH", permission = "platform:graph:log:view")
    GRAPH_LOG_VIEW,

    @MenuNode(code = "platform:form", name = "动态表单", type = MenuNodeType.MENU,
            path = "/platform/form", component = "platform/form/index.vue",
            icon = "i-ri:survey-line", sort = 62)
    FORM,

    @MenuNode(code = "platform:form:designer", name = "表单设计器", type = MenuNodeType.MENU,
            path = "/platform/form/designer", component = "platform/form/designer.vue",
            icon = "i-ri:drag-drop-line", sort = 61, visible = false,
            permission = "platform:form:edit")
    FORM_DESIGNER,

    @MenuNode(code = "platform:form:view", name = "查看动态表单", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:view")
    FORM_VIEW,

    @MenuNode(code = "platform:form:edit", name = "编辑动态表单", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:edit")
    FORM_EDIT,

    @MenuNode(code = "platform:form:publish", name = "发布动态表单", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:publish")
    FORM_PUBLISH,

    @MenuNode(code = "platform:form:delete", name = "删除动态表单", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:delete")
    FORM_DELETE,

    @MenuNode(code = "platform:form:submission:view", name = "查看表单提交", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:submission:view")
    FORM_SUBMISSION_VIEW,

    @MenuNode(code = "platform:form:submission:export", name = "导出表单提交", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:submission:export")
    FORM_SUBMISSION_EXPORT,

    @MenuNode(code = "platform:form:statistics:view", name = "查看表单统计", type = MenuNodeType.BUTTON,
            parentName = "FORM", permission = "platform:form:statistics:view")
    FORM_STATISTICS_VIEW,

    @MenuNode(code = "platform:theme-center", name = "主题中心", type = MenuNodeType.MENU,
            path = "/platform/theme-center", component = "platform/theme-center/index.vue",
            icon = "i-ri:palette-line", sort = 60)
    THEME_CENTER,

    @MenuNode(code = "platform:theme-center:view", name = "查看主题中心", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:theme-center:view")
    THEME_CENTER_VIEW,

    @MenuNode(code = "platform:theme-center:use", name = "切换整站主题", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:theme-center:use")
    THEME_CENTER_USE,

    @MenuNode(code = "platform:theme-center:config", name = "配置主题", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:theme-center:config")
    THEME_CENTER_CONFIG,

    @MenuNode(code = "platform:cms:view", name = "查看内容定制", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:cms:view")
    CMS_VIEW,

    @MenuNode(code = "platform:cms:edit", name = "编辑内容定制", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:cms:edit")
    CMS_EDIT,

    @MenuNode(code = "platform:cms:publish", name = "发布内容页面", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:cms:publish")
    CMS_PUBLISH,

    @MenuNode(code = "platform:cms:delete", name = "删除内容页面", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:cms:delete")
    CMS_DELETE,

    @MenuNode(code = "platform:ai:generate", name = "AI 生成内容", type = MenuNodeType.BUTTON,
            parentName = "THEME_CENTER", permission = "platform:ai:generate")
    AI_GENERATE,

    @MenuNode(code = "platform:agent", name = "Agent 应用", type = MenuNodeType.MENU,
            path = "/platform/agent", component = "platform/agent/index.vue",
            icon = "i-ri:robot-2-line", sort = 59)
    AGENT,

    @MenuNode(code = "platform:agent:editor", name = "Agent 编排", type = MenuNodeType.MENU,
            path = "/platform/agent/editor", component = "platform/agent/editor.vue",
            icon = "i-ri:node-tree", sort = 58, visible = false, permission = "platform:agent:edit")
    AGENT_EDITOR,

    @MenuNode(code = "platform:agent:tools", name = "Agent 工具", type = MenuNodeType.MENU,
            path = "/platform/agent/tools", component = "platform/agent/tools.vue",
            icon = "i-ri:tools-line", sort = 57, permission = "platform:agent:tool:view")
    AGENT_TOOLS,

    @MenuNode(code = "platform:agent:view", name = "查看 Agent 应用", type = MenuNodeType.BUTTON,
            parentName = "AGENT", permission = "platform:agent:view")
    AGENT_VIEW,

    @MenuNode(code = "platform:agent:edit", name = "编辑 Agent 应用", type = MenuNodeType.BUTTON,
            parentName = "AGENT", permission = "platform:agent:edit")
    AGENT_EDIT,

    @MenuNode(code = "platform:agent:publish", name = "发布 Agent 应用", type = MenuNodeType.BUTTON,
            parentName = "AGENT", permission = "platform:agent:publish")
    AGENT_PUBLISH,

    @MenuNode(code = "platform:agent:delete", name = "删除 Agent 应用", type = MenuNodeType.BUTTON,
            parentName = "AGENT", permission = "platform:agent:delete")
    AGENT_DELETE,

    @MenuNode(code = "platform:agent:run", name = "运行 Agent 应用", type = MenuNodeType.BUTTON,
            parentName = "AGENT", permission = "platform:agent:run")
    AGENT_RUN,

    @MenuNode(code = "platform:agent:tool:view", name = "查看 Agent 工具", type = MenuNodeType.BUTTON,
            parentName = "AGENT_TOOLS", permission = "platform:agent:tool:view")
    AGENT_TOOL_VIEW,

    @MenuNode(code = "platform:agent:tool:edit", name = "编辑 Agent 工具", type = MenuNodeType.BUTTON,
            parentName = "AGENT_TOOLS", permission = "platform:agent:tool:edit")
    AGENT_TOOL_EDIT,

    @MenuNode(code = "platform:agent:tool:delete", name = "删除 Agent 工具", type = MenuNodeType.BUTTON,
            parentName = "AGENT_TOOLS", permission = "platform:agent:tool:delete")
    AGENT_TOOL_DELETE
}
