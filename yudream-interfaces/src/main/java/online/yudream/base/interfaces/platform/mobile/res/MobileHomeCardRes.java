package online.yudream.base.interfaces.platform.mobile.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用向主页注册的卡片响应（plugin.yml mobile.home.cards）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileHomeCardRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String description;
    private String icon;
    private String route;
}
