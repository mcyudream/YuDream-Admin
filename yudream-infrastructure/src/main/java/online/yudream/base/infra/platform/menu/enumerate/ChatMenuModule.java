package online.yudream.base.infra.platform.menu.enumerate;

import online.yudream.base.domain.system.menu.anno.MenuModule;
import online.yudream.base.domain.system.menu.anno.MenuNode;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

/**
 * AI 助手顶级菜单模块：大图标直达全屏聊天页，无中间二级菜单。
 * 权限码沿用 platform:chat:*，保证既有角色授权不失效。
 */
@MenuModule(code = "assistant", name = "AI 助手", icon = "i-ri:sparkling-2-fill", sort = 1)
public enum ChatMenuModule {

    @MenuNode(code = "platform:chat", name = "AI 助手", type = MenuNodeType.MENU,
            path = "/platform/chat", component = "platform/chat/index.vue",
            icon = "i-ri:chat-3-line", sort = 1)
    CHAT,

    @MenuNode(code = "platform:chat:use", name = "使用 AI 助手", type = MenuNodeType.BUTTON,
            parentName = "CHAT", permission = "platform:chat:use")
    CHAT_USE,

    @MenuNode(code = "platform:chat:session:view", name = "查看 AI 助手会话", type = MenuNodeType.BUTTON,
            parentName = "CHAT", permission = "platform:chat:session:view")
    CHAT_SESSION_VIEW,

    @MenuNode(code = "platform:chat:session:edit", name = "管理 AI 助手会话", type = MenuNodeType.BUTTON,
            parentName = "CHAT", permission = "platform:chat:session:edit")
    CHAT_SESSION_EDIT,

    @MenuNode(code = "platform:chat:quota:config", name = "配置 AI 助手额度", type = MenuNodeType.BUTTON,
            parentName = "CHAT", permission = "platform:chat:quota:config")
    CHAT_QUOTA_CONFIG
}
