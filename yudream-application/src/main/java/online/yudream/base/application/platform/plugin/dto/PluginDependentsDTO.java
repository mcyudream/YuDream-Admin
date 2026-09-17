package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 指定插件的直接依赖方视图：卸载/删除前用于级联确认弹窗。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDependentsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private List<DependentDTO> dependents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependentDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String name;
        /** true=硬依赖（目标不可用时无法运行），false=软依赖（可降级）。 */
        private boolean required;
        private boolean loaded;
        private boolean enabled;
    }
}
