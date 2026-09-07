package online.yudream.base.application.platform.devtools.cmd;

import lombok.Data;

/**
 * 从父目录批量登记开发模式项目：扫描子目录中的插件模块并去重。
 */
@Data
public class PluginDevProjectBatchCmd {

    /** 父目录（宿主机绝对路径），其自身若是插件模块也会被纳入 */
    private String path;
}
