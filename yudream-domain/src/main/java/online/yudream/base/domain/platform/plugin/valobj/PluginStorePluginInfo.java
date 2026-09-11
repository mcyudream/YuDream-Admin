package online.yudream.base.domain.platform.plugin.valobj;

import lombok.Data;

/** sourceCode/sourceName 标识该条目来自哪个市场源；内置直连路径下为 null。 */
@Data
public class PluginStorePluginInfo {

    private String code;
    private PluginStorePluginDescriptor descriptor;
    private String sourceCode;
    private String sourceName;
}
