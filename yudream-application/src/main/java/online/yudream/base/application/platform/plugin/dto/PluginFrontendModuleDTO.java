package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendModuleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String entry;
    private String moduleName;
    private String sdkVersion;
    private String integrity;
    private String menuTitle;
    private String menuIcon;
    private Integer menuSort;
    private String parentCode;
    private Boolean visible;
    private MenuStatus status;

    @Builder.Default
    private List<PluginFrontendRouteDTO> routes = new ArrayList<>();
}
