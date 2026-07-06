package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendRouteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String path;
    private String name;
    private String title;
    private String icon;
    private String parentPath;
    private String parentTitle;
    private String parentIcon;
    private Integer parentSort;
    private String component;
    private String permission;
    private Integer sort;
}
