package online.yudream.base.plugin.spi.guide;

import java.util.List;

/**
 * 插件新手引导注册项：声明式分步引导。宿主在用户进入 {@code routePath} 匹配页面
 * 且未完成时自动播放，也可由全局 AI 前端工具触发；完成态按用户在宿主侧记录。
 */
public record PluginGuide(
        String code,
        String title,
        String routePath,
        String permission,
        int sort,
        List<PluginGuideStep> steps
) {
    public PluginGuide {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
