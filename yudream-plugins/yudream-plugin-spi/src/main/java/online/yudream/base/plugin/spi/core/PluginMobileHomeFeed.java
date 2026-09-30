package online.yudream.base.plugin.spi.core;

/**
 * plugin.yml {@code mobile.home.feed} 移动端内容源端点的 SPI 声明。
 * <p>
 * endpoint 必须以 {@code /} 开头，指向插件 API 根下的相对端点，宿主 App 据此拉取
 * 插件的真实内容（帖子等）渲染首页信息流；title 为可选的信息流分节标题。
 */
public record PluginMobileHomeFeed(
        String endpoint,
        String title
) {
}
