package online.yudream.base.infra.platform.menu.enumerate;

import online.yudream.base.domain.system.menu.anno.MenuModule;
import online.yudream.base.domain.system.menu.anno.MenuNode;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

@MenuModule(code = "platform", name = "平台能力", icon = "i-ri:node-tree", sort = 2)
public enum PlatformMenuModule {

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

    @MenuNode(code = "platform:cms", name = "内容定制", type = MenuNodeType.MENU,
            path = "/platform/cms", component = "platform/cms/index.vue",
            icon = "i-ri:layout-masonry-line", sort = 60)
    CMS,

    @MenuNode(code = "platform:cms:view", name = "查看内容定制", type = MenuNodeType.BUTTON,
            parentName = "CMS", permission = "platform:cms:view")
    CMS_VIEW,

    @MenuNode(code = "platform:cms:edit", name = "编辑内容定制", type = MenuNodeType.BUTTON,
            parentName = "CMS", permission = "platform:cms:edit")
    CMS_EDIT,

    @MenuNode(code = "platform:cms:publish", name = "发布内容页面", type = MenuNodeType.BUTTON,
            parentName = "CMS", permission = "platform:cms:publish")
    CMS_PUBLISH,

    @MenuNode(code = "platform:cms:delete", name = "删除内容页面", type = MenuNodeType.BUTTON,
            parentName = "CMS", permission = "platform:cms:delete")
    CMS_DELETE,

    @MenuNode(code = "platform:ai:generate", name = "AI 生成内容", type = MenuNodeType.BUTTON,
            parentName = "CMS", permission = "platform:ai:generate")
    AI_GENERATE
}
