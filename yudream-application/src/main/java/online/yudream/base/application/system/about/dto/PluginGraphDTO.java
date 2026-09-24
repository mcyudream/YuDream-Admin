package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 插件依赖与装载状态图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginGraphDTO {

    private List<PluginGraphNodeDTO> nodes;
    private List<PluginGraphEdgeDTO> edges;
}
