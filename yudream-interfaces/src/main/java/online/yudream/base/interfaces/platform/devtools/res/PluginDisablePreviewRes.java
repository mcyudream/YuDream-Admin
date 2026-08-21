package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 插件禁用级联预览响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDisablePreviewRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标插件编码 */
    private String code;

    /** 已启用的传递硬依赖方，按建议禁用顺序排序（链路上最外层的依赖方在前） */
    private List<String> blockers;

    /** 已启用的直接软依赖方：禁用后其可选集成将降级不可用 */
    private List<String> softDependents;

    /** 已加载的直接硬/软依赖方：存在时卸载（含重载）将被运行时拒绝 */
    private List<String> unloadBlockers;
}
