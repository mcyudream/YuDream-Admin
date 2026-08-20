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
