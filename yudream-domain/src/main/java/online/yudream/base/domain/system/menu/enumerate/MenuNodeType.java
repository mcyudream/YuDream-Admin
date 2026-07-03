package online.yudream.base.domain.system.menu.enumerate;

/**
 * 菜单节点类型。
 */
public enum MenuNodeType {
    /**
     * 分组/目录，用于组织下级节点。
     */
    CATEGORY,

    /**
     * 布局节点，只承载子路由，不直接指向业务页面。
     */
    LAYOUT,

    /**
     * 页面菜单，会生成实际路由。
     */
    MENU,

    /**
     * 外链菜单，会生成带 meta.link 的路由。
     */
    LINK,

    /**
     * 按钮级权限，只参与权限控制，不生成路由。
     */
    BUTTON
}
