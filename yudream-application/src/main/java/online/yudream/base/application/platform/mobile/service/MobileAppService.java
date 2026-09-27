package online.yudream.base.application.platform.mobile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.mobile.assembler.MobileAssembler;
import online.yudream.base.application.platform.mobile.cmd.MobileDeviceRegisterCmd;
import online.yudream.base.application.platform.mobile.cmd.MobileDeviceUnregisterCmd;
import online.yudream.base.application.platform.mobile.dto.MobileDeviceDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestDTO;
import online.yudream.base.application.platform.mobile.dto.MobileManifestEntryDTO;
import online.yudream.base.application.platform.mobile.query.MobileManifestQuery;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.platform.mobile.valobj.MobileCapabilityConfig;
import online.yudream.base.domain.platform.mobile.valobj.MobilePluginSupport;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 移动应用应用服务：manifest 聚合与设备注册。
 * <p>
 * 每个用例前走能力应用闸门 ensureEnabled；iOS 席位由能力配置 iosEnabled 控制，
 * 键缺失或为空时运行时回落 false。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileAppService {

    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final PluginModuleRepo pluginModuleRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final MobileDeviceRepo mobileDeviceRepo;

    @Transactional(readOnly = true)
    public MobileManifestDTO manifest(MobileManifestQuery query) {
        ensureCapabilityEnabled();
        if (query == null || query.getPlatform() == null) {
            throw new BizException("platform 参数仅支持 android/ios");
        }
        // iOS 通道由能力配置 iosEnabled 控制：关闭时 iOS 席位对外不存在
        if (query.getPlatform() == MobilePlatform.IOS && !iosEnabled()) {
            return new MobileManifestDTO(query.getPlatform().token(), List.of());
        }
        Set<String> capabilities = normalizeCapabilities(query.getCapabilitySet());
        Map<String, PluginFrontendModuleInfo> frontendByCode = frontendModulesByCode();
        List<MobileManifestEntryDTO> entries = pluginModuleRepo.findAll().stream()
                .filter(PluginModule::enabled)
                .filter(module -> module.mobileSupport().availableFor(query.getPlatform(), capabilities, query.getHostVersion()))
                .sorted(java.util.Comparator.comparing(PluginModule::getCode))
                .map(module -> toEntry(module, frontendByCode.get(module.getCode())))
                .toList();
        return new MobileManifestDTO(query.getPlatform().token(), entries);
    }

    @Transactional
    public MobileDeviceDTO register(Long userId, MobileDeviceRegisterCmd cmd) {
        ensureCapabilityEnabled();
        if (userId == null) {
            throw new BizException("移动设备必须绑定登录用户");
        }
        if (cmd == null || cmd.getPlatform() == null) {
            throw new BizException("移动设备平台仅支持 android/ios");
        }
        if (cmd.getPushToken() == null || cmd.getPushToken().isBlank()) {
            throw new BizException("推送 token 不能为空");
        }
        String pushToken = cmd.getPushToken().trim();
        MobileDevice device = mobileDeviceRepo.findByUserIdAndPushToken(userId, pushToken)
                .map(existing -> {
                    existing.belongsTo(userId);
                    existing.refresh(cmd.getPlatform(), cmd.getPushChannel(),
                            cmd.getDeviceName(), cmd.getAppVersion(), cmd.getHostVersion());
                    return existing;
                })
                .orElseGet(() -> MobileDevice.register(userId, cmd.getPlatform(), cmd.getPushChannel(),
                        pushToken, cmd.getDeviceName(), cmd.getAppVersion(), cmd.getHostVersion()));
        return MobileAssembler.toDTO(mobileDeviceRepo.save(device));
    }

    @Transactional
    public void unregister(Long userId, MobileDeviceUnregisterCmd cmd) {
        ensureCapabilityEnabled();
        if (userId == null) {
            throw new BizException("移动设备必须绑定登录用户");
        }
        if (cmd == null || !StringUtils.hasText(cmd.getPushToken())) {
            throw new BizException("推送 token 不能为空");
        }
        MobileDevice device = mobileDeviceRepo.findByUserIdAndPushToken(userId, cmd.getPushToken().trim())
                .orElseThrow(() -> new BizException("未找到该设备的注册记录"));
        device.belongsTo(userId);
        device.unregister();
        mobileDeviceRepo.save(device);
    }

    @Transactional(readOnly = true)
    public List<MobileDeviceDTO> listByUser(Long userId) {
        ensureCapabilityEnabled();
        if (userId == null) {
            throw new BizException("当前用户不能为空");
        }
        return mobileDeviceRepo.findByUserId(userId).stream()
                .map(MobileAssembler::toDTO)
                .toList();
    }

    private MobileManifestEntryDTO toEntry(PluginModule module, PluginFrontendModuleInfo frontend) {
        MobilePluginSupport support = module.mobileSupport();
        return MobileAssembler.toEntryDTO(
                module.getCode(),
                module.getPluginVersion(),
                frontend == null ? "" : frontend.assetRevision(),
                pluginRuntimeGateway.mobileAssetSha256(module.getCode(), "remoteEntry.js"),
                pluginRuntimeGateway.mobileAssetSha256(module.getCode(), "style.css"),
                support
        );
    }

    private Map<String, PluginFrontendModuleInfo> frontendModulesByCode() {
        return pluginRuntimeGateway.frontendModules().stream()
                .collect(Collectors.toMap(PluginFrontendModuleInfo::pluginCode, Function.identity(), (left, right) -> left));
    }

    private Set<String> normalizeCapabilities(Set<String> capabilitySet) {
        if (capabilitySet == null || capabilitySet.isEmpty()) {
            return Set.of();
        }
        return capabilitySet.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** iOS 开关读取：能力配置键缺失/为空/非法时回落 false。 */
    private boolean iosEnabled() {
        Optional<CapabilityModule> module = capabilityModuleRepo.findByCode(MobileCapabilityConfig.CAPABILITY_CODE);
        return module.map(value -> MobileCapabilityConfig.iosEnabled(value.getConfig())).orElse(false);
    }

    private void ensureCapabilityEnabled() {
        capabilityAppService.ensureEnabled(MobileCapabilityConfig.CAPABILITY_CODE, "移动应用");
    }
}
