package online.yudream.base.plugin.spi.core;

import java.util.List;

/**
 * plugin.yml 可选 mobile 块的 SPI 镜像：插件对移动 App 的支持声明。
 * <p>
 * 整个 mobile 块缺省表示插件未声明移动支持；块存在时各字段按宿主约定回落默认值
 * （platforms 缺省 android+ios、minHostVersion 缺省 1.0.0、requiredNativeCapabilities 缺省空）。
 */
public record PluginMobileSupport(
        List<String> platforms,
        String minHostVersion,
        List<String> requiredNativeCapabilities
) {

    public static final String DEFAULT_MIN_HOST_VERSION = "1.0.0";

    public PluginMobileSupport {
        platforms = platforms == null || platforms.isEmpty() ? List.of("android", "ios") : List.copyOf(platforms);
        minHostVersion = minHostVersion == null || minHostVersion.isBlank()
                ? DEFAULT_MIN_HOST_VERSION
                : minHostVersion.trim();
        requiredNativeCapabilities = requiredNativeCapabilities == null
                ? List.of()
                : List.copyOf(requiredNativeCapabilities);
    }
}
