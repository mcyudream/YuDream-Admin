package online.yudream.base.domain.platform.mobile.valobj;

/**
 * 管理入口卡（plugin.yml mobile.admin.cards[] 的领域镜像）：
 * 管理员功能在移动端「管理」聚合中的注册声明。
 * permission 为打开该入口所需权限码；manifest 组装时按当前用户权限逐卡过滤，
 * 无权限的入口不下发（前端不可见即不可达）。
 */
public record MobileAdminCard(
        String id,
        String title,
        String description,
        String icon,
        String route,
        String permission
) {

    public MobileAdminCard {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        icon = icon == null ? "" : icon;
        route = route == null ? "" : route;
        permission = permission == null ? "" : permission;
    }
}
