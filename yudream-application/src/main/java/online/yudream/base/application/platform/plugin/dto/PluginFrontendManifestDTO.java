package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendManifestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private String sdkVersion = "1.3.0";

    @Builder.Default
    private List<PluginFrontendModuleDTO> modules = new ArrayList<>();

    /** 已启用插件声明的全局挂件；匿名访客下发时恒为空。 */
    @Builder.Default
    private List<PluginGlobalWidgetDTO> globalWidgets = new ArrayList<>();
}
