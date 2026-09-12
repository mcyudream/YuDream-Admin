package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

@Data
public class PluginMarketSourceUpdateCmd {

    private Long id;
    private String name;
    /** 内置源固定 LOCAL，更新时忽略该字段。 */
    private String type;
    /** 内置源无远端地址，更新时忽略该字段。 */
    private String rootUrl;
    /** 空/空白表示保留现有令牌。 */
    private String token;
    private Integer sortOrder;
}
