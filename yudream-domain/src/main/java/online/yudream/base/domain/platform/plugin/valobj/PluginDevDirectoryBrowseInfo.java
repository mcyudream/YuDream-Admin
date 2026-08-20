package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 宿主机目录浏览结果快照，供开发者工具面板的选择目录弹窗逐层导航。
 * rootList 为 true 时 path 为空、entries 为文件系统根（Windows 盘符）；
 * hasPom/hasPluginYml/inferredCode 描述当前目录本身的插件模块标记。
 */
public record PluginDevDirectoryBrowseInfo(
        String path,
        String parent,
        boolean rootList,
        boolean hasPom,
        boolean hasPluginYml,
        String inferredCode,
        List<PluginDevDirectoryEntryInfo> entries
) {
}
