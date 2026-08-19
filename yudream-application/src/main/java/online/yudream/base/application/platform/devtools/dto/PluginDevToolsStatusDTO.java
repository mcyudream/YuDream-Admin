package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发者工具状态：开发模式与 Agent 追踪开关、开发项目清单与运行时计数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDevToolsStatusDTO {

    private boolean devModeEnabled;
    private boolean traceEnabled;

    @Builder.Default
    private List<PluginDevProjectInfo> devProjects = new ArrayList<>();

    private int installedCount;
    private int loadedCount;
    private int enabledCount;
}
