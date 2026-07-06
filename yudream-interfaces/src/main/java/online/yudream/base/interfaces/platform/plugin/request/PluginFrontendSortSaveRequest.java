package online.yudream.base.interfaces.platform.plugin.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PluginFrontendSortSaveRequest {
    private String moduleName;
    private Integer menuSort;
    private List<PluginFrontendRouteSortSaveRequest> routes = new ArrayList<>();
}
