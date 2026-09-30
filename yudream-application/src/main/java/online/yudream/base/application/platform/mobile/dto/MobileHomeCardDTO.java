package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 移动端主页卡片：插件 plugin.yml mobile.home.cards[] 单项的清单下发形态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileHomeCardDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String description;
    private String icon;
    private String route;
}
