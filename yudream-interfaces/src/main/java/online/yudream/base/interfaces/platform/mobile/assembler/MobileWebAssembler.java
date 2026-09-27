package online.yudream.base.interfaces.platform.mobile.assembler;

import online.yudream.base.application.platform.mobile.cmd.MobileDeviceRegisterCmd;
import online.yudream.base.application.platform.mobile.cmd.MobileDeviceUnregisterCmd;
import online.yudream.base.application.platform.mobile.dto.MobileDeviceDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestEntryDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestDTO;
import online.yudream.base.application.platform.mobile.query.MobileManifestQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.interfaces.platform.mobile.request.MobileDeviceRegisterRequest;
import online.yudream.base.interfaces.platform.mobile.request.MobileDeviceUnregisterRequest;
import online.yudream.base.interfaces.platform.mobile.res.MobileDeviceRes;
import online.yudream.base.interfaces.platform.mobile.res.MobileManifestEntryRes;
import online.yudream.base.interfaces.platform.mobile.res.MobileManifestRes;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * mobile 接口装配：request -> cmd/query、应用 DTO -> res。
 */
public class MobileWebAssembler {

    private MobileWebAssembler() {
    }

    public static MobileManifestQuery toManifestQuery(String platform, String hostVersion, String capabilitySet) {
        if (!StringUtils.hasText(platform)) {
            throw new BizException("platform 参数仅支持 android/ios");
        }
        return new MobileManifestQuery(
                MobilePlatform.fromToken(platform.trim()),
                StringUtils.hasText(hostVersion) ? hostVersion.trim() : null,
                parseCapabilitySet(capabilitySet)
        );
    }

    public static MobileDeviceRegisterCmd toRegisterCmd(MobileDeviceRegisterRequest request) {
        return new MobileDeviceRegisterCmd(
                MobilePlatform.fromToken(request.getPlatform()),
                request.getPushChannel(),
                request.getPushToken(),
                request.getDeviceName(),
                request.getAppVersion(),
                request.getHostVersion()
        );
    }

    public static MobileDeviceUnregisterCmd toUnregisterCmd(MobileDeviceUnregisterRequest request) {
        return new MobileDeviceUnregisterCmd(request.getPushToken());
    }

    public static MobileManifestRes toRes(MobileManifestDTO dto) {
        return MobileManifestRes.builder()
                .platform(dto.getPlatform())
                .entries(dto.getEntries().stream().map(MobileWebAssembler::toEntryRes).toList())
                .build();
    }

    public static MobileDeviceRes toRes(MobileDeviceDTO dto) {
        return MobileDeviceRes.builder()
                .id(dto.getId())
                .platform(dto.getPlatform())
                .pushChannel(dto.getPushChannel())
                .pushToken(dto.getPushToken())
                .deviceName(dto.getDeviceName())
                .appVersion(dto.getAppVersion())
                .hostVersion(dto.getHostVersion())
                .registeredAt(dto.getRegisteredAt())
                .lastSeenAt(dto.getLastSeenAt())
                .build();
    }

    public static List<MobileDeviceRes> toDeviceResList(List<MobileDeviceDTO> list) {
        return list == null ? List.of() : list.stream().map(MobileWebAssembler::toRes).toList();
    }

    private static MobileManifestEntryRes toEntryRes(MobileManifestEntryDTO entry) {
        return MobileManifestEntryRes.builder()
                .code(entry.getCode())
                .version(entry.getVersion())
                .assetRevision(entry.getAssetRevision())
                .remoteEntrySha256(entry.getRemoteEntrySha256())
                .remoteEntryUrl(entry.getRemoteEntryUrl())
                .minHostVersion(entry.getMinHostVersion())
                .styleUrl(entry.getStyleUrl())
                .platforms(entry.getPlatforms())
                .requiredNativeCapabilities(entry.getRequiredNativeCapabilities())
                .build();
    }

    /** capabilitySet 逗号分隔可空；空集表示不要求任何原生能力。 */
    private static Set<String> parseCapabilitySet(String capabilitySet) {
        if (!StringUtils.hasText(capabilitySet)) {
            return Set.of();
        }
        return Arrays.stream(capabilitySet.split(","))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
}
