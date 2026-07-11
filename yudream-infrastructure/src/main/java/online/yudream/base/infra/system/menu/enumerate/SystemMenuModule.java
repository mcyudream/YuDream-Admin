package online.yudream.base.infra.system.menu.enumerate;

import online.yudream.base.domain.system.menu.anno.MenuModule;
import online.yudream.base.domain.system.menu.anno.MenuNode;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;

@MenuModule(code = "system", name = "系统管理", icon = "i-ri:settings-3-line", sort = 1)
public enum SystemMenuModule {

    @MenuNode(code = "system:personnel", name = "人员管理", type = MenuNodeType.LAYOUT,
            path = "/system/personnel", component = "Layout", icon = "i-ri:team-line", sort = 40)
    PERSONNEL,

    @MenuNode(code = "system:user", name = "用户管理", type = MenuNodeType.MENU,
            parentName = "PERSONNEL", path = "/system/user", component = "system/user/index.vue",
            icon = "i-ri:user-settings-line", sort = 30)
    USER,

    @MenuNode(code = "system:user:create", name = "新增用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:create")
    USER_CREATE,

    @MenuNode(code = "system:user:edit", name = "编辑用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:edit")
    USER_EDIT,

    @MenuNode(code = "system:user:assign-role", name = "分配用户角色", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:assign-role")
    USER_ASSIGN_ROLE,

    @MenuNode(code = "system:user:assign-dept", name = "分配用户部门", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:assign-dept")
    USER_ASSIGN_DEPT,

    @MenuNode(code = "system:user:impersonate", name = "伪装用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:impersonate")
    USER_IMPERSONATE,

    @MenuNode(code = "system:user:delete", name = "删除用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:delete")
    USER_DELETE,

    @MenuNode(code = "system:user:export", name = "导出用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:export")
    USER_EXPORT,

    @MenuNode(code = "system:user:import", name = "导入用户", type = MenuNodeType.BUTTON,
            parentName = "USER", permission = "system:user:import")
    USER_IMPORT,

    @MenuNode(code = "system:role", name = "角色管理", type = MenuNodeType.MENU,
            parentName = "PERSONNEL", path = "/system/role", component = "system/role/index.vue",
            icon = "i-ri:shield-user-line", sort = 20)
    ROLE,

    @MenuNode(code = "system:role:create", name = "新增角色", type = MenuNodeType.BUTTON,
            parentName = "ROLE", permission = "system:role:create")
    ROLE_CREATE,

    @MenuNode(code = "system:role:edit", name = "编辑角色", type = MenuNodeType.BUTTON,
            parentName = "ROLE", permission = "system:role:edit")
    ROLE_EDIT,

    @MenuNode(code = "system:role:delete", name = "删除角色", type = MenuNodeType.BUTTON,
            parentName = "ROLE", permission = "system:role:delete")
    ROLE_DELETE,

    @MenuNode(code = "system:role:export", name = "导出角色", type = MenuNodeType.BUTTON,
            parentName = "ROLE", permission = "system:role:export")
    ROLE_EXPORT,

    @MenuNode(code = "system:role:import", name = "导入角色", type = MenuNodeType.BUTTON,
            parentName = "ROLE", permission = "system:role:import")
    ROLE_IMPORT,

    @MenuNode(code = "system:dept", name = "部门管理", type = MenuNodeType.MENU,
            parentName = "PERSONNEL", path = "/system/dept", component = "system/dept/index.vue",
            icon = "i-ri:organization-chart", sort = 10)
    DEPT,

    @MenuNode(code = "system:dept:create", name = "新增部门", type = MenuNodeType.BUTTON,
            parentName = "DEPT", permission = "system:dept:create")
    DEPT_CREATE,

    @MenuNode(code = "system:dept:edit", name = "编辑部门", type = MenuNodeType.BUTTON,
            parentName = "DEPT", permission = "system:dept:edit")
    DEPT_EDIT,

    @MenuNode(code = "system:dept:delete", name = "删除部门", type = MenuNodeType.BUTTON,
            parentName = "DEPT", permission = "system:dept:delete")
    DEPT_DELETE,

    @MenuNode(code = "system:dept:export", name = "导出部门", type = MenuNodeType.BUTTON,
            parentName = "DEPT", permission = "system:dept:export")
    DEPT_EXPORT,

    @MenuNode(code = "system:dept:import", name = "导入部门", type = MenuNodeType.BUTTON,
            parentName = "DEPT", permission = "system:dept:import")
    DEPT_IMPORT,

    @MenuNode(code = "system:config", name = "系统配置", type = MenuNodeType.LAYOUT,
            path = "/system/config", component = "Layout", icon = "i-ri:settings-4-line", sort = 30)
    CONFIG,

    @MenuNode(code = "system:setting", name = "系统设置", type = MenuNodeType.MENU,
            parentName = "CONFIG", path = "/system/setting", component = "system/setting/index.vue",
            icon = "i-ri:tools-line", sort = 30)
    SETTING,

    @MenuNode(code = "system:setting:view", name = "查看系统设置", type = MenuNodeType.BUTTON,
            parentName = "SETTING", permission = "system:setting:view")
    SETTING_VIEW,

    @MenuNode(code = "system:setting:edit", name = "编辑系统设置", type = MenuNodeType.BUTTON,
            parentName = "SETTING", permission = "system:setting:edit")
    SETTING_EDIT,

    @MenuNode(code = "system:setting:upload", name = "上传系统资源", type = MenuNodeType.BUTTON,
            parentName = "SETTING", permission = "system:setting:upload")
    SETTING_UPLOAD,

    @MenuNode(code = "system:command", name = "指令管理", type = MenuNodeType.MENU,
            parentName = "CONFIG", path = "/system/command", component = "system/command/index.vue",
            icon = "i-ri:terminal-box-line", sort = 25)
    COMMAND,

    @MenuNode(code = "system:command:view", name = "查看指令", type = MenuNodeType.BUTTON,
            parentName = "COMMAND", permission = "system:command:view")
    COMMAND_VIEW,

    @MenuNode(code = "system:command:edit", name = "配置指令", type = MenuNodeType.BUTTON,
            parentName = "COMMAND", permission = "system:command:edit")
    COMMAND_EDIT,

    @MenuNode(code = "system:theme", name = "主题配置", type = MenuNodeType.MENU,
            parentName = "CONFIG", path = "/system/theme", component = "system/theme/index.vue",
            icon = "i-ri:palette-line", sort = 20)
    THEME,

    @MenuNode(code = "system:theme:view", name = "查看主题配置", type = MenuNodeType.BUTTON,
            parentName = "THEME", permission = "system:setting:theme:view")
    THEME_VIEW,

    @MenuNode(code = "system:theme:edit", name = "编辑主题配置", type = MenuNodeType.BUTTON,
            parentName = "THEME", permission = "system:setting:theme:edit")
    THEME_EDIT,

    @MenuNode(code = "system:security", name = "安全中心", type = MenuNodeType.MENU,
            parentName = "CONFIG", path = "/system/security", component = "system/security/index.vue",
            icon = "i-ri:key-2-line", sort = 15)
    SECURITY,

    @MenuNode(code = "system:security:view", name = "查看安全中心", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:view")
    SECURITY_VIEW,

    @MenuNode(code = "system:security:edit", name = "编辑安全策略", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:edit")
    SECURITY_EDIT,

    @MenuNode(code = "system:security:api-key:create", name = "创建API Key", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:api-key:create")
    SECURITY_API_KEY_CREATE,

    @MenuNode(code = "system:security:api-key:revoke", name = "吊销API Key", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:api-key:revoke")
    SECURITY_API_KEY_REVOKE,

    @MenuNode(code = "system:security:oauth:view", name = "查看OAuth配置", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:oauth:view")
    SECURITY_OAUTH_VIEW,

    @MenuNode(code = "system:security:oauth:edit", name = "编辑OAuth配置", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:oauth:edit")
    SECURITY_OAUTH_EDIT,

    @MenuNode(code = "system:security:passkey:view", name = "查看Passkey", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:passkey:view")
    SECURITY_PASSKEY_VIEW,

    @MenuNode(code = "system:security:passkey:revoke", name = "吊销Passkey", type = MenuNodeType.BUTTON,
            parentName = "SECURITY", permission = "system:security:passkey:revoke")
    SECURITY_PASSKEY_REVOKE,

    @MenuNode(code = "system:menu", name = "菜单管理", type = MenuNodeType.MENU,
            parentName = "CONFIG", path = "/system/menu", component = "system/menu/index.vue",
            icon = "i-ri:menu-2-line", sort = 10)
    MENU,

    @MenuNode(code = "system:menu:create", name = "新增菜单", type = MenuNodeType.BUTTON,
            parentName = "MENU", permission = "system:menu:create")
    MENU_CREATE,

    @MenuNode(code = "system:menu:edit", name = "编辑菜单", type = MenuNodeType.BUTTON,
            parentName = "MENU", permission = "system:menu:edit")
    MENU_EDIT,

    @MenuNode(code = "system:menu:delete", name = "删除菜单", type = MenuNodeType.BUTTON,
            parentName = "MENU", permission = "system:menu:delete")
    MENU_DELETE,

    @MenuNode(code = "system:menu:export", name = "导出菜单", type = MenuNodeType.BUTTON,
            parentName = "MENU", permission = "system:menu:export")
    MENU_EXPORT,

    @MenuNode(code = "system:menu:import", name = "导入菜单", type = MenuNodeType.BUTTON,
            parentName = "MENU", permission = "system:menu:import")
    MENU_IMPORT,

    @MenuNode(code = "system:monitor", name = "系统监控", type = MenuNodeType.LAYOUT,
            path = "/system/monitor", component = "Layout", icon = "i-ri:dashboard-3-line", sort = 20)
    MONITOR,

    @MenuNode(code = "system:redis-monitor", name = "Redis监控", type = MenuNodeType.MENU,
            parentName = "MONITOR", path = "/system/redis-monitor", component = "system/redis-monitor/index.vue",
            icon = "i-ri:database-2-line", sort = 20)
    REDIS_MONITOR,

    @MenuNode(code = "system:redis-monitor:view", name = "查看Redis监控", type = MenuNodeType.BUTTON,
            parentName = "REDIS_MONITOR", permission = "system:monitor:redis:view")
    REDIS_MONITOR_VIEW,

    @MenuNode(code = "system:online-user", name = "在线用户", type = MenuNodeType.MENU,
            parentName = "MONITOR", path = "/system/online-user", component = "system/online-user/index.vue",
            icon = "i-ri:radar-line", sort = 10)
    ONLINE_USER,

    @MenuNode(code = "system:online-user:view", name = "查看在线用户", type = MenuNodeType.BUTTON,
            parentName = "ONLINE_USER", permission = "system:monitor:online:view")
    ONLINE_USER_VIEW,

    @MenuNode(code = "system:online-user:kickout", name = "强制用户下线", type = MenuNodeType.BUTTON,
            parentName = "ONLINE_USER", permission = "system:monitor:online:kickout")
    ONLINE_USER_KICKOUT,

    @MenuNode(code = "system:online-user:export", name = "导出在线用户", type = MenuNodeType.BUTTON,
            parentName = "ONLINE_USER", permission = "system:monitor:online:export")
    ONLINE_USER_EXPORT,

    @MenuNode(code = "system:logs", name = "日志管理", type = MenuNodeType.LAYOUT,
            path = "/system/logs", component = "Layout", icon = "i-ri:file-list-3-line", sort = 10)
    LOGS,

    @MenuNode(code = "system:api-log", name = "接口日志", type = MenuNodeType.MENU,
            parentName = "LOGS", path = "/system/api-log", component = "system/api-log/index.vue",
            icon = "i-ri:file-search-line", sort = 20)
    API_LOG,

    @MenuNode(code = "system:api-log:view", name = "查看接口日志", type = MenuNodeType.BUTTON,
            parentName = "API_LOG", permission = "system:monitor:api-log:view")
    API_LOG_VIEW,

    @MenuNode(code = "system:api-log:export", name = "导出接口日志", type = MenuNodeType.BUTTON,
            parentName = "API_LOG", permission = "system:monitor:api-log:export")
    API_LOG_EXPORT,

    @MenuNode(code = "system:api-log:delete", name = "删除接口日志", type = MenuNodeType.BUTTON,
            parentName = "API_LOG", permission = "system:monitor:api-log:delete")
    API_LOG_DELETE,

    @MenuNode(code = "system:login-log", name = "登录日志", type = MenuNodeType.MENU,
            parentName = "LOGS", path = "/system/login-log", component = "system/login-log/index.vue",
            icon = "i-ri:login-box-line", sort = 10)
    LOGIN_LOG,

    @MenuNode(code = "system:login-log:view", name = "查看登录日志", type = MenuNodeType.BUTTON,
            parentName = "LOGIN_LOG", permission = "system:monitor:login-log:view")
    LOGIN_LOG_VIEW,

    @MenuNode(code = "system:login-log:export", name = "导出登录日志", type = MenuNodeType.BUTTON,
            parentName = "LOGIN_LOG", permission = "system:monitor:login-log:export")
    LOGIN_LOG_EXPORT,

    @MenuNode(code = "system:login-log:delete", name = "删除登录日志", type = MenuNodeType.BUTTON,
            parentName = "LOGIN_LOG", permission = "system:monitor:login-log:delete")
    LOGIN_LOG_DELETE,
}
