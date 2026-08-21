package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 插件禁用级联预览：禁用/卸载前列出受影响的依赖方，供开发者在操作前评估爆炸半径。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDisablePreviewDTO {

    /** 目标插件编码 */
    private String code;

    /**
     * 已启用的传递硬依赖方，按建议禁用顺序排序（链路上最外层的依赖方在前，顺次禁用后即可禁用目标）。
     * 运行时会拒绝禁用存在已启用硬依赖方的插件，因此需按此顺序先行禁用。
     */
    private List<String> blockers;

    /** 已启用的直接软依赖方：禁用目标不阻塞，但这些插件的可选集成将降级不可用 */
    private List<String> softDependents;

    /** 已加载的直接硬/软依赖方：与运行时卸载校验一致，存在时卸载（含重载）将被拒绝 */
    private List<String> unloadBlockers;
}
