package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 开发模式插件项目配置快照，供开发者工具面板展示与状态判断。
 */
public record PluginDevProjectInfo(
        String code,
        String path,
        String frontendDist,
        boolean autoCompile
) {
}
