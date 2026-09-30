package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页轮播图条目（mobile-app 能力配置 homeBanners，JSON 数组项）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileBannerDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 图片地址（站点资产路径或完整 URL） */
    private String imageUrl;

    /** 标题（可空；展示在图上时由 App 决定样式） */
    private String title;

    /** 点击跳转的应用内路由（可空；形如 /posts/latest） */
    private String route;
}
