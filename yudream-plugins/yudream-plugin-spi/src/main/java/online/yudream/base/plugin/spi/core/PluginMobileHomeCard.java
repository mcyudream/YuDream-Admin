package online.yudream.base.plugin.spi.core;

/**
 * plugin.yml {@code mobile.home.cards[]} 单张移动端主页卡片的 SPI 声明。
 * <p>
 * id 为插件内唯一稳定标识（小写字母/数字/连字符）；route 必须以 {@code /} 开头，
 * 指向插件移动端页面路径。
 */
public record PluginMobileHomeCard(
        String id,
        String title,
        String description,
        String icon,
        String route
) {
}
