package online.yudream.base.interfaces.platform.mobile.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理入口卡响应：plugin.yml mobile.admin.cards 单项（已按当前用户权限过滤）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileAdminCardRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String description;
    private String icon;
    private String route;
    private String permission;
}
