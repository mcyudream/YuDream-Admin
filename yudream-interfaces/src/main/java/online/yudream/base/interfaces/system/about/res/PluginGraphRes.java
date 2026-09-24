package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 插件依赖与装载状态图视图：节点 status ∈ ENABLED/LOADED/INSTALLED/DISABLED/ERROR/MISSING，边 kind ∈ HARD/SOFT。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginGraphRes {

    private List<PluginGraphNode> nodes;
    private List<PluginGraphEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PluginGraphNode {
        private String code;
        private String name;
        private String version;
        private String status;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PluginGraphEdge {
        private String source;
        private String target;
        private String kind;
    }
}
