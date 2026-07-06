package online.yudream.base.application.platform.plugin.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendSortSaveCmd {
    private String moduleName;
    private Integer menuSort;

    @Builder.Default
    private List<PluginFrontendRouteSortSaveCmd> routes = new ArrayList<>();
}
