package online.yudream.base.domain.system.user.enumerate;

/**
 * 权限来源。
 */
public enum PermissionSource {
    /**
     * 来自注解扫描（@PermissionRegister / @SaCheckPermission）。
     */
    ANNOTATION,

    /**
     * 来自菜单初始化。
     */
    MENU,

    /**
     * 人工创建/其他来源。
     */
    MANUAL
}
