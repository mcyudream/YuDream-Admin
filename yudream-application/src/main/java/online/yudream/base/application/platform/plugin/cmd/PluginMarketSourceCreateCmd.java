package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

@Data
public class PluginMarketSourceCreateCmd {

    /** 源标识（小写字母/数字/连字符），创建后不可修改；内置源固定为 default。 */
    private String code;
    private String name;
    /** STATIC_INDEX 或 V2_API；禁止创建 LOCAL（仅内置本机源）。 */
    private String type;
    private String rootUrl;
    private String token;
    private Integer sortOrder;
}
