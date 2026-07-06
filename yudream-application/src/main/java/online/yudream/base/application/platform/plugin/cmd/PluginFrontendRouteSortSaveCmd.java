package online.yudream.base.application.platform.plugin.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendRouteSortSaveCmd {
    private String path;
    private String name;
    private Integer sort;
    private Integer parentSort;
}
