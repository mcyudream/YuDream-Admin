package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

@Data
public class PluginMarketSourceCreateCmd {

    /** 源标识（小写字母/数字/连字符），创建后不可修改；内置源固定为 default。 */
    private String code;
    private String name;
    private String rootUrl;
    private String token;
    private Integer sortOrder;
}
