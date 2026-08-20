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

    /** 宿主运行形态：SOURCE / JAR */
    private String hostRunMode;
    /** 开发模式生效值是否来自自动检测 */
    private boolean devModeAuto;
    /** 面板维护的开发项目清单文件绝对路径 */
    private String devProjectStoreFile;

    @Builder.Default
    private List<PluginDevProjectInfo> devProjects = new ArrayList<>();

    private int installedCount;
    private int loadedCount;
    private int enabledCount;
}
