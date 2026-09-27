package online.yudream.base.plugin.spi.core;

import java.util.List;

/**
 * plugin.yml 可选 mobile 块的 SPI 镜像：插件对移动 App 的支持声明。
 * <p>
 * 整个 mobile 块缺省表示插件未声明移动支持；块存在时各字段按宿主约定回落默认值
 * （platforms 缺省 android+ios、minHostVersion 缺省 1.0.0、requiredNativeCapabilities 缺省空）。
 * <p>
 * name/description/icon 为移动端展示信息（全部可选）；homeCards 为移动端主页卡片
 * 声明（可选，缺省空列表）；homeFeed 为移动端首页信息流内容源端点声明（可选，缺省 null）。
 */
public record PluginMobileSupport(
        List<String> platforms,
        String minHostVersion,
        List<String> requiredNativeCapabilities,
        String name,
        String description,
        String icon,
        List<PluginMobileHomeCard> homeCards,
        PluginMobileHomeFeed homeFeed
) {

    public static final String DEFAULT_MIN_HOST_VERSION = "1.0.0";

    /** 兼容 2.x 既有构造形态：未声明 homeFeed 的插件按缺省 null 处理。 */
    public PluginMobileSupport(List<String> platforms,
                               String minHostVersion,
                               List<String> requiredNativeCapabilities,
                               String name,
                               String description,
                               String icon,
                               List<PluginMobileHomeCard> homeCards) {
        this(platforms, minHostVersion, requiredNativeCapabilities, name, description, icon, homeCards, null);
    }

    public PluginMobileSupport {
        platforms = platforms == null || platforms.isEmpty() ? List.of("android", "ios") : List.copyOf(platforms);
        minHostVersion = minHostVersion == null || minHostVersion.isBlank()
                ? DEFAULT_MIN_HOST_VERSION
                : minHostVersion.trim();
        requiredNativeCapabilities = requiredNativeCapabilities == null
                ? List.of()
                : List.copyOf(requiredNativeCapabilities);
        name = name == null || name.isBlank() ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        icon = icon == null || icon.isBlank() ? null : icon.trim();
        homeCards = homeCards == null ? List.of() : List.copyOf(homeCards);
        homeFeed = homeFeed == null ? null : homeFeed;
    }
}
