package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 指定已安装插件的前置依赖状态：启用前用于依赖预览与软依赖选择。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDependencyStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private List<DependencyDTO> dependencies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DependencyDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String name;
        private boolean required;
        private String range;
        private boolean installed;
        private String installedVersion;
        private boolean versionSatisfied;
        private boolean loaded;
        private boolean enabled;
        /** 市场是否提供该依赖（用于缺失依赖的快捷安装）。 */
        private boolean storeAvailable;
        private String storeVersion;
        private String storeSourceCode;
        private String storeSourceName;
    }
}
