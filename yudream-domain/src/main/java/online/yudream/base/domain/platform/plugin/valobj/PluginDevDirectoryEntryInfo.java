package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 宿主机目录条目快照：仅描述目录本身与其插件模块标记，不携带任何文件内容。
 * hasPom/hasPluginYml/inferredCode 供开发者工具面板标记可登记的插件模块目录。
 */
public record PluginDevDirectoryEntryInfo(
        String name,
        String path,
        boolean hasPom,
        boolean hasPluginYml,
        String inferredCode
) {
}
