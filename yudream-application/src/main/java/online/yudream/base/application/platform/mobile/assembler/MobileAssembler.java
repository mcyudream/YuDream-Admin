package online.yudream.base.application.platform.mobile.assembler;

import online.yudream.base.application.platform.mobile.dto.MobileDeviceDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestEntryDTO;
import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.valobj.MobilePluginSupport;

import java.util.Optional;

/**
 * mobile 应用装配：聚合/值对象到应用 DTO 的转换。
 */
public class MobileAssembler {

    private MobileAssembler() {
    }

    public static MobileDeviceDTO toDTO(MobileDevice device) {
        return MobileDeviceDTO.builder()
                .id(device.getId() == null ? null : String.valueOf(device.getId()))
                .platform(device.getPlatform() == null ? null : device.getPlatform().token())
                .pushChannel(device.getPushChannel())
                .pushToken(device.getPushToken())
                .deviceName(device.getDeviceName())
                .appVersion(device.getAppVersion())
                .hostVersion(device.getHostVersion())
                .registeredAt(device.getRegisteredAt())
                .lastSeenAt(device.getLastSeenAt())
                .build();
    }

    public static MobileManifestEntryDTO toEntryDTO(String pluginCode,
                                                    String pluginVersion,
                                                    String assetRevision,
                                                    Optional<String> remoteEntrySha256,
                                                    Optional<String> styleAssetSha256,
                                                    MobilePluginSupport support) {
        String styleUrl = styleAssetSha256.isPresent()
                ? mobileAssetUrl(pluginCode, "style.css")
                : null;
        return MobileManifestEntryDTO.builder()
                .code(pluginCode)
                .version(pluginVersion)
                .assetRevision(assetRevision)
                .remoteEntrySha256(remoteEntrySha256.orElse(null))
                .remoteEntryUrl(mobileAssetUrl(pluginCode, "remoteEntry.js"))
                .minHostVersion(support.minHostVersion())
                .platforms(support.platformTokens())
                .requiredNativeCapabilities(support.normalizedCapabilityTokens())
                .styleUrl(styleUrl)
                .build();
    }

    /** 移动产物下载地址：复用既有插件资产下发通道，约定 mobile/ 前缀映射 frontend-mobile 根。 */
    public static String mobileAssetUrl(String pluginCode, String assetPath) {
        return "/api/platform/plugins/" + pluginCode + "/assets/mobile/" + assetPath;
    }

    public static String platformToken(MobilePlatform platform) {
        return platform == null ? null : platform.token();
    }
}
