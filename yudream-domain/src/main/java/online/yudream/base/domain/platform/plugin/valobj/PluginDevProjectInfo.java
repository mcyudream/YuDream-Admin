package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.platform.plugin.enumerate.PluginDevProjectSource;

/**
 * 开发模式插件项目配置快照，供开发者工具面板展示与状态判断。
 * path 为插件模块根目录；pathExists/classesBuilt/descriptorReady 为登记时刻的目录有效性检查。
 */
public record PluginDevProjectInfo(
        String code,
        String path,
        String frontendDist,
        boolean autoCompile,
        PluginDevProjectSource source,
        boolean pathExists,
        boolean classesBuilt,
        boolean descriptorReady
) {
}
