package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发者工具状态响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDevToolsStatusRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean devModeEnabled;
    private boolean traceEnabled;

    @Builder.Default
    private List<PluginDevProjectInfo> devProjects = new ArrayList<>();

    private int installedCount;
    private int loadedCount;
    private int enabledCount;
}
