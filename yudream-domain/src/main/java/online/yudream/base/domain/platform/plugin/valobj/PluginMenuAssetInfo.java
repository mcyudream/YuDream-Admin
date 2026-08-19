package online.yudream.base.domain.platform.plugin.valobj;

public record PluginMenuAssetInfo(
        String title,
        String path,
        String icon,
        String permission,
        String parentPath,
        Integer sort
) {
}
