package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

/**
 * 插件新手引导运行时下发信息：宿主在用户进入匹配页面且未完成时自动播放的分步引导。
 */
public record PluginGuideInfo(
        String pluginCode,
        String code,
        String title,
        String routePath,
        String permission,
        int sort,
        List<PluginGuideStepInfo> steps
) {
}
