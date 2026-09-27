package online.yudream.base.domain.platform.mobile.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 插件对移动 App 的支持声明（plugin.yml mobile 块的领域镜像）。
 * <p>
 * 承载 manifest 过滤的三个判定：平台匹配、原生能力子集、宿主版本下限。
 * {@link #declared()} 为 false 表示插件整个 mobile 块缺省，不进入移动清单。
 */
public record MobilePluginSupport(
        boolean declared,
        List<MobilePlatform> platforms,
        String minHostVersion,
        List<String> requiredNativeCapabilities
) {

    public static final String DEFAULT_MIN_HOST_VERSION = "1.0.0";

    /** plugin.yml 未声明 mobile 块时的空声明。 */
    public static MobilePluginSupport undeclared() {
        return new MobilePluginSupport(false, List.of(), DEFAULT_MIN_HOST_VERSION, List.of());
    }

    /** 声明了 mobile 块但未填字段时按约定回落默认值。 */
    public static MobilePluginSupport declared(List<MobilePlatform> platforms, String minHostVersion,
                                               List<String> requiredNativeCapabilities) {
        List<MobilePlatform> safePlatforms = platforms == null || platforms.isEmpty()
                ? List.of(MobilePlatform.ANDROID, MobilePlatform.IOS)
                : List.copyOf(platforms);
        String safeMinHostVersion = minHostVersion == null || minHostVersion.isBlank()
                ? DEFAULT_MIN_HOST_VERSION
                : minHostVersion.trim();
        List<String> safeCapabilities = requiredNativeCapabilities == null
                ? List.of()
                : requiredNativeCapabilities.stream().filter(item -> item != null && !item.isBlank()).toList();
        return new MobilePluginSupport(true, safePlatforms, safeMinHostVersion, safeCapabilities);
    }

    public MobilePluginSupport {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        requiredNativeCapabilities = requiredNativeCapabilities == null ? List.of() : List.copyOf(requiredNativeCapabilities);
    }

    /** 注册期校验：platforms 只允许 android/ios，其余值在插件加载时即失败。 */
    public static MobilePlatform requireLegalPlatformToken(String token) {
        if (!MobilePlatform.isLegalToken(token)) {
            throw new BizException("插件 mobile 声明的 platforms 仅支持 android/ios：" + token);
        }
        return MobilePlatform.fromToken(token);
    }

    public boolean supports(MobilePlatform platform) {
        return declared && platform != null && platforms.contains(platform);
    }

    /** capabilitySet 是否覆盖插件要求的全部原生能力（插件要求为空即通过）。 */
    public boolean satisfiesNativeCapabilities(Set<String> providedCapabilities) {
        if (requiredNativeCapabilities.isEmpty()) {
            return true;
        }
        return providedCapabilities != null && providedCapabilities.containsAll(requiredNativeCapabilities);
    }

    public boolean satisfiesHostVersion(String hostVersion) {
        return MobileSemanticVersion.satisfiesHostVersion(hostVersion, minHostVersion);
    }

    /** manifest 过滤的纯函数判定：平台、能力、版本三者同时满足才下发该插件。 */
    public boolean availableFor(MobilePlatform platform, Set<String> providedCapabilities, String hostVersion) {
        return supports(platform)
                && satisfiesNativeCapabilities(providedCapabilities)
                && satisfiesHostVersion(hostVersion);
    }

    public List<String> platformTokens() {
        return platforms.stream().map(MobilePlatform::token).toList();
    }

    public List<String> normalizedCapabilityTokens() {
        return requiredNativeCapabilities.stream()
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .toList();
    }
}
