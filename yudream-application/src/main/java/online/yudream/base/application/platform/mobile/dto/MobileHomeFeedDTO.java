package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 移动端首页信息流内容源：插件 plugin.yml mobile.home.feed 的清单下发形态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileHomeFeedDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件 API 根下的相对端点，宿主 App 据此拉取信息流数据。 */
    private String endpoint;
    /** 可选的信息流分节标题（未声明时为 null）。 */
    private String title;
}
