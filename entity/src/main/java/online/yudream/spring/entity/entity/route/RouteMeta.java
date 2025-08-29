package online.yudream.spring.entity.entity.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteMeta {
        private String activeIcon;
        private String activePath;
        private Boolean affixTab;
        private Integer affixTabOrder;
        private java.util.List<String> authority;
        private String badge;
        private BadgeType badgeType;
        private String badgeVariants;       // 字符串或预设名，后端不限制
        private Boolean fullPathKey;
        private Boolean hideChildrenInMenu;
        private Boolean hideInBreadcrumb;
        private Boolean hideInMenu;
        private Boolean hideInTab;
        private Object icon;                // 存字符串；如需更严谨可改为 String
        private String iframeSrc;
        private Boolean ignoreAccess;
        private Boolean keepAlive;
        private String link;
        private Boolean loaded;
        private Integer maxNumOfOpenTab;
        private Boolean menuVisibleWithForbidden;
        private Boolean noBasicLayout;      // 顶级路由才生效（由前端处理）
        private Boolean openInNewWindow;
        private Integer order;              // 菜单排序
        private Map<String, Object> query;
        private String title;
}
