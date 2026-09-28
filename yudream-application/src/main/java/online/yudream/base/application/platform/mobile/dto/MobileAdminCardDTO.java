package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理入口卡：插件 plugin.yml mobile.admin.cards[] 单项的清单下发形态
 * （已按当前用户权限过滤，仅保留有权打开的入口）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileAdminCardDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String description;
    private String icon;
    private String route;
    private String permission;
}
