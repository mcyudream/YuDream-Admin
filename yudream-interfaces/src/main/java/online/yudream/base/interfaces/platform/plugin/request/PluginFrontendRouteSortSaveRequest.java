package online.yudream.base.interfaces.platform.plugin.request;

import lombok.Data;

@Data
public class PluginFrontendRouteSortSaveRequest {
    private String path;
    private String name;
    private Integer sort;
    private Integer parentSort;
}
