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
    CAPABILITY_TEST
}
