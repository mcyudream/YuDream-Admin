package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

@Data
public class PluginMarketSourceUpdateCmd {

    private Long id;
    private String name;
    /** 内置源的 rootUrl 由宿主配置镜像，更新时忽略该字段。 */
    private String rootUrl;
    /** 空/空白表示保留现有令牌。 */
    private String token;
    private Integer sortOrder;
}
