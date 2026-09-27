package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 匿名站点发现信息：移动 App 添加域时探测"这是一个 YuDream 站点"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileSiteInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String siteName;

    private String logo;

    private String favicon;

    /** 后端框架版本，取不到时为空串。 */
    private String version;

    /** mobile-app 能力应用闸门状态：未启用为 false。 */
    private boolean mobileEnabled;

    /** 登录页 hero 背景图（能力配置，空=未定制）。 */
    private String loginHeroImage;

    /** 登录页 hero 底色（能力配置，空=未定制）。 */
    private String loginHeroBackground;

}
