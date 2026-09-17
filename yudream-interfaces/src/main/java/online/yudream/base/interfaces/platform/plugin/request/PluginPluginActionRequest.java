package online.yudream.base.interfaces.platform.plugin.request;

import lombok.Data;

import java.util.List;

/** 插件运维动作可选参数：启用时可显式携带软依赖，卸载时可级联依赖方。 */
@Data
public class PluginPluginActionRequest {

    /** 启用时一并启用的软依赖编码清单（须已安装）。 */
    private List<String> includeSoftDependencies;

    /** 卸载/删除时是否级联处理已加载依赖方（硬依赖方停机保持禁用，软依赖方降级恢复）。 */
    private Boolean cascade;
}
