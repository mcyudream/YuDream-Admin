package online.yudream.base.interfaces.platform.mobile.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 移动端首页信息流内容源响应（plugin.yml mobile.home.feed）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileHomeFeedRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 插件 API 根下的相对端点，宿主 App 据此拉取信息流数据。 */
    private String endpoint;
    /** 可选的信息流分节标题（未声明时为 null）。 */
    private String title;
}
