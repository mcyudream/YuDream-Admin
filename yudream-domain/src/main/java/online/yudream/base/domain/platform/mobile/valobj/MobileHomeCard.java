package online.yudream.base.domain.platform.mobile.valobj;

/**
 * 移动端主页卡片声明（plugin.yml mobile.home.cards[] 的领域镜像）。
 * <p>
 * id 为插件内唯一稳定标识；route 以 {@code /} 开头，指向插件移动端页面。
 */
public record MobileHomeCard(
        String id,
        String title,
        String description,
        String icon,
        String route
) {

    public MobileHomeCard {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        icon = icon == null ? "" : icon;
        route = route == null ? "" : route;
    }
}
