package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdatePlanDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateResultDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDetailDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginVersionDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;
import online.yudream.base.domain.platform.plugin.valobj.SemVer;
import online.yudream.base.domain.platform.plugin.valobj.SemVerRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class PluginStoreAppService {

    private final PluginStoreGateway pluginStoreGateway;
    private final PluginAppService pluginAppService;

    @Value("${yudream.platform.plugin.upload-directory:plugins}")
    private String uploadDirectory;

    @Value("${yudream.platform.plugin.compatibility.host:1.0.0}")
    private String hostVersion = "1.0.0";

    @Value("${yudream.platform.plugin.compatibility.spi:2.6.0}")
    private String spiVersion = "2.6.0";

    @Value("${yudream.platform.plugin.compatibility.frontend-sdk:1.0.1}")
    private String frontendSdkVersion = "1.0.1";

    @Transactional(readOnly = true)
    public List<PluginStorePluginDTO> list() {
        return pluginStoreGateway.list().stream()
                .map(PluginAssembler::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PluginStorePluginDetailDTO detail(String code) {
        var detail = storeDetail(code);
        List<PluginModuleDTO> localPlugins = detail.versions().stream()
                .anyMatch(version -> !version.descriptor().dependencies().isEmpty()) ? localPlugins() : List.of();
        return PluginStorePluginDetailDTO.builder()
                .code(detail.code())
                .versions(detail.versions().stream()
                        .map(version -> toDTO(version, evaluateInstallability(version.descriptor(), localPlugins)))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PluginMarketplaceUpdateDTO> updates() {
        List<PluginModuleDTO> localPlugins = installedPlugins();
        return localPlugins.stream()
                .map(localPlugin -> update(localPlugin, localPlugins))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PluginMarketplaceUpdatePlanDTO> updatePlans() {
        List<PluginModuleDTO> localPlugins = installedPlugins();
        return localPlugins.stream()
                .filter(plugin -> StringUtils.hasText(plugin.getCode()))
                .map(plugin -> createUpdatePlan(plugin, localPlugins, null))
                .flatMap(java.util.Optional::stream)
                .filter(this::isUpgrade)
                .toList();
    }

    @Transactional(readOnly = true)
    public PluginMarketplaceUpdatePlanDTO updatePlan(String code, String targetVersion) {
        String normalizedCode = normalizeCode(code);
        List<PluginModuleDTO> localPlugins = installedPlugins();
        PluginModuleDTO localPlugin = localPlugins.stream()
                .filter(plugin -> normalizedCode.equals(plugin.getCode()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        PluginMarketplaceUpdatePlanDTO plan = createUpdatePlan(localPlugin, localPlugins, targetVersion)
                .orElseThrow(PluginStoreAppService::unavailable);
        if (!isUpgrade(plan)) {
            throw unavailable();
        }
        return plan;
    }

    @Transactional
    public PluginMarketplaceUpdateResultDTO rollback(String code) {
        String normalizedCode = normalizeCode(code);
        return rollbackLocal(normalizedCode, null);
    }

    @Transactional
    public PluginMarketplaceUpdateResultDTO rollback(String code, String targetVersion) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(targetVersion)) {
            throw unavailable();
        }
        return rollbackLocal(normalizedCode, targetVersion.trim());
    }

    private PluginMarketplaceUpdateResultDTO rollbackLocal(String normalizedCode, String targetVersion) {
        boolean installed = installedPlugins().stream()
                .anyMatch(plugin -> normalizedCode.equals(plugin.getCode()));
        if (!installed) {
            throw unavailable();
        }
        List<PluginModuleDTO> modules = targetVersion == null
                ? pluginAppService.rollbackStoreJar(normalizedCode)
                : pluginAppService.rollbackStoreJar(normalizedCode, targetVersion);
        return PluginMarketplaceUpdateResultDTO.builder()
                .modules(modules)
                .requiresRestart(true)
                .build();
    }

    @Transactional
    public PluginMarketplaceUpdateResultDTO update(String code, String targetVersion) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(targetVersion)) {
            throw unavailable();
        }
        List<PluginModuleDTO> localPlugins = installedPlugins();
        PluginModuleDTO localPlugin = localPlugins.stream()
                .filter(plugin -> normalizedCode.equals(plugin.getCode()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        var detail = storeDetail(normalizedCode);
        PluginMarketplaceUpdatePlanDTO plan = createUpdatePlan(localPlugin, localPlugins, detail, targetVersion)
                .orElseThrow(PluginStoreAppService::unavailable);
        if (!isUpgrade(plan) || StringUtils.hasText(plan.getBlockedReason())) {
            throw unavailable();
        }
        PluginStorePluginVersion storeVersion = detail.versions().stream()
                .filter(version -> plan.getToVersion().equals(version.releaseVersion()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        return PluginMarketplaceUpdateResultDTO.builder()
                .modules(downloadAndUpdateStoreVersion(storeVersion.descriptor()))
                .requiresRestart(true)
                .build();
    }

    private java.util.Optional<PluginMarketplaceUpdatePlanDTO> createUpdatePlan(PluginModuleDTO localPlugin,
                                                                                  List<PluginModuleDTO> localPlugins,
                                                                                  String targetVersion) {
        String normalizedCode = normalizeCode(localPlugin.getCode());
        return pluginStoreGateway.detail(normalizedCode)
                .flatMap(detail -> createUpdatePlan(localPlugin, localPlugins, detail, targetVersion));
    }

    private java.util.Optional<PluginMarketplaceUpdatePlanDTO> createUpdatePlan(PluginModuleDTO localPlugin,
                                                                                  List<PluginModuleDTO> localPlugins,
                                                                                  online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail detail,
                                                                                  String targetVersion) {
        try {
            PluginStorePluginVersion target = selectUpdatePlanVersion(detail, targetVersion);
            Installability installability = evaluateInstallability(target.descriptor(), localPlugins);
            List<PluginStorePluginDependency> dependencies = target.descriptor().dependencies();
            return java.util.Optional.of(PluginMarketplaceUpdatePlanDTO.builder()
                    .code(localPlugin.getCode())
                    .fromVersion(localPlugin.getVersion())
                    .toVersion(target.releaseVersion())
                    .changeType(changeType(localPlugin.getVersion(), target.releaseVersion()))
                    .requiredDependencies(dependencies.stream().filter(PluginStorePluginDependency::required)
                            .map(PluginAssembler::toDTO).toList())
                    .optionalDependencies(dependencies.stream().filter(dependency -> !dependency.required())
                            .map(PluginAssembler::toDTO).toList())
                    .affectedEnabledPlugins(affectedEnabledPlugins(localPlugins, localPlugin.getCode()))
                    .requiresRestart(true)
                    .blockedReason(installability.disabledReason())
                    .warnings(installability.unavailableOptionalDependencies().stream()
                            .map(dependency -> "可选依赖 " + dependency.code() + " 不可用")
                            .toList())
                    .build());
        } catch (BizException exception) {
            return java.util.Optional.empty();
        }
    }

    @Transactional
    public List<PluginModuleDTO> install(String code, String version) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(version)) {
            throw unavailable();
        }
        PluginStorePluginVersion storeVersion = storeDetail(normalizedCode).versions().stream()
                .filter(item -> version.trim().equals(item.releaseVersion()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        return installStoreVersion(storeVersion.descriptor());
    }

    private List<PluginModuleDTO> installStoreVersion(PluginStorePluginDescriptor descriptor) {
        List<PluginModuleDTO> localPlugins = descriptor.dependencies().isEmpty() ? List.of() : localPlugins();
        Installability installability = evaluateInstallability(descriptor, localPlugins);
        if (!installability.installable()) {
            throw unavailable();
        }
        logOptionalDependencyWarnings(descriptor, installability);
        return downloadAndInstallStoreVersion(descriptor);
    }

    private List<PluginModuleDTO> downloadAndInstallStoreVersion(PluginStorePluginDescriptor descriptor) {
        return downloadStoreVersion(descriptor, false);
    }

    private List<PluginModuleDTO> downloadAndUpdateStoreVersion(PluginStorePluginDescriptor descriptor) {
        return downloadStoreVersion(descriptor, true);
    }

    private List<PluginModuleDTO> downloadStoreVersion(PluginStorePluginDescriptor descriptor, boolean update) {
        Path stagedJar = null;
        try {
            Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            stagedJar = Files.createTempFile(directory, ".plugin-store-", ".tmp");
            pluginStoreGateway.downloadJar(descriptor, stagedJar);
            List<PluginModuleDTO> result = update
                    ? pluginAppService.updateStoreJar(stagedJar, descriptor.code(), descriptor.version(), descriptor.main())
                    : pluginAppService.installStoreJar(stagedJar, descriptor.code(), descriptor.version(), descriptor.main());
            stagedJar = null;
            return result;
        } catch (IOException e) {
            throw new BizException("插件 JAR 下载失败：" + e.getMessage());
        } finally {
            deleteQuietly(stagedJar);
        }
    }

    private java.util.Optional<PluginMarketplaceUpdateDTO> update(PluginModuleDTO localPlugin,
                                                                       List<PluginModuleDTO> localPlugins) {
        if (!StringUtils.hasText(localPlugin.getCode())) {
            return java.util.Optional.empty();
        }
        return pluginStoreGateway.detail(localPlugin.getCode()).flatMap(detail -> detail.versions().stream()
                .map(this::parseVersion)
                .flatMap(java.util.Optional::stream)
                .max(java.util.Comparator.comparing(ParsedStoreVersion::version))
                .map(latest -> toUpdateDTO(localPlugin, latest.storeVersion(), localPlugins)));
    }

    private PluginStorePluginVersion selectUpdatePlanVersion(
            online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail detail, String targetVersion) {
        if (StringUtils.hasText(targetVersion)) {
            return detail.versions().stream()
                    .filter(version -> targetVersion.trim().equals(version.releaseVersion()))
                    .filter(version -> parseVersion(version).isPresent())
                    .findFirst()
                    .orElseThrow(PluginStoreAppService::unavailable);
        }
        return detail.versions().stream()
                .map(this::parseVersion)
                .flatMap(java.util.Optional::stream)
                .max(java.util.Comparator.comparing(ParsedStoreVersion::version))
                .map(ParsedStoreVersion::storeVersion)
                .orElseThrow(PluginStoreAppService::unavailable);
    }

    private List<String> affectedEnabledPlugins(List<PluginModuleDTO> localPlugins, String code) {
        Set<String> affectedCodes = new LinkedHashSet<>();
        affectedCodes.add(code);
        boolean changed;
        do {
            changed = localPlugins.stream()
                    .filter(plugin -> !affectedCodes.contains(plugin.getCode()))
                    .filter(plugin -> affectedCodes.stream().anyMatch(dependency -> dependsOn(plugin, dependency)))
                    .map(PluginModuleDTO::getCode)
                    .filter(affectedCodes::add)
                    .findAny()
                    .isPresent();
        } while (changed);
        return localPlugins.stream()
                .filter(plugin -> !code.equals(plugin.getCode()))
                .filter(plugin -> affectedCodes.contains(plugin.getCode()))
                .filter(plugin -> plugin.isEnabled()
                        || plugin.getStatus() == online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED)
                .map(PluginModuleDTO::getCode)
                .toList();
    }

    private boolean dependsOn(PluginModuleDTO plugin, String code) {
        return (plugin.getDependencies() != null && plugin.getDependencies().contains(code))
                || (plugin.getSoftDependencies() != null && plugin.getSoftDependencies().contains(code));
    }

    private String changeType(String fromVersion, String toVersion) {
        try {
            SemVer from = SemVer.parse(fromVersion);
            SemVer to = SemVer.parse(toVersion);
            int comparison = to.compareTo(from);
            if (comparison < 0) {
                return "DOWNGRADE";
            }
            if (comparison == 0) {
                return "NONE";
            }
            if (from.major() != to.major()) {
                return "MAJOR";
            }
            if (from.minor() != to.minor()) {
                return "MINOR";
            }
            return "PATCH";
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private boolean isUpgrade(PluginMarketplaceUpdatePlanDTO plan) {
        try {
            return SemVer.parse(plan.getToVersion()).compareTo(SemVer.parse(plan.getFromVersion())) > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isDowngrade(PluginMarketplaceUpdatePlanDTO plan) {
        try {
            return SemVer.parse(plan.getToVersion()).compareTo(SemVer.parse(plan.getFromVersion())) < 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private java.util.Optional<ParsedStoreVersion> parseVersion(PluginStorePluginVersion storeVersion) {
        try {
            return java.util.Optional.of(new ParsedStoreVersion(storeVersion, SemVer.parse(storeVersion.releaseVersion())));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private PluginMarketplaceUpdateDTO toUpdateDTO(PluginModuleDTO localPlugin, PluginStorePluginVersion latest,
                                                     List<PluginModuleDTO> localPlugins) {
        Installability installability;
        try {
            installability = evaluateInstallability(latest.descriptor(), localPlugins);
        } catch (BizException exception) {
            installability = new Installability(false, "插件版本或兼容性数据无效", List.of());
        }
        boolean updateAvailable = false;
        try {
            updateAvailable = SemVer.parse(latest.releaseVersion()).compareTo(SemVer.parse(localPlugin.getVersion())) > 0;
        } catch (IllegalArgumentException exception) {
            log.warn("Installed plugin {} has invalid version {}; update comparison skipped", localPlugin.getCode(), localPlugin.getVersion());
        }
        return PluginMarketplaceUpdateDTO.builder()
                .code(localPlugin.getCode())
                .currentVersion(localPlugin.getVersion())
                .latestVersion(latest.descriptor().version())
                .latestReleaseVersion(latest.releaseVersion())
                .latestDisplayName(latest.descriptor().displayName())
                .updateAvailable(updateAvailable)
                .compatible(installability.installable())
                .blockedReason(installability.disabledReason())
                .build();
    }

    private PluginStorePluginVersionDTO toDTO(PluginStorePluginVersion version, Installability installability) {
        PluginStorePluginVersionDTO dto = PluginAssembler.toDTO(version);
        dto.setInstallable(installability.installable());
        dto.setInstallDisabledReason(installability.disabledReason());
        dto.getDescriptor().getDependencies().stream()
                .filter(dependency -> installability.unavailableOptionalDependencies().stream()
                        .anyMatch(unavailable -> unavailable.code().equals(dependency.getCode())))
                .forEach(dependency -> {
                    dependency.setWarning(true);
                    dependency.setWarningReason("可选依赖不可用");
                });
        return dto;
    }

    private Installability evaluateInstallability(PluginStorePluginDescriptor descriptor, List<PluginModuleDTO> localPlugins) {
        String compatibilityFailure = compatibilityFailure(descriptor.compatibility());
        if (compatibilityFailure != null) {
            return new Installability(false, compatibilityFailure, List.of());
        }
        List<PluginStorePluginDependency> unavailableOptionalDependencies = descriptor.dependencies().stream()
                .filter(dependency -> !isAvailable(dependency, localPlugins))
                .filter(dependency -> !dependency.required())
                .toList();
        for (PluginStorePluginDependency dependency : descriptor.dependencies()) {
            if (dependency.required() && !isAvailable(dependency, localPlugins)) {
                return new Installability(false, "必需依赖 " + dependency.code() + " 不可用", unavailableOptionalDependencies);
            }
        }
        return new Installability(true, null, unavailableOptionalDependencies);
    }

    private String compatibilityFailure(PluginStorePluginCompatibility compatibility) {
        if (compatibility == null) {
            return null;
        }
        if (!matches(compatibility.host(), hostVersion)) {
            return "宿主版本不满足兼容性要求";
        }
        if (!matches(compatibility.spi(), spiVersion)) {
            return "SPI 版本不满足兼容性要求";
        }
        if (!matches(compatibility.frontendSdk(), frontendSdkVersion)) {
            return "前端 SDK 版本不满足兼容性要求";
        }
        return null;
    }

    private boolean isAvailable(PluginStorePluginDependency dependency, List<PluginModuleDTO> localPlugins) {
        return localPlugins.stream().anyMatch(plugin -> dependency.code().equals(plugin.getCode())
                && matches(dependency.range(), plugin.getVersion()));
    }

    private List<PluginModuleDTO> localPlugins() {
        List<PluginModuleDTO> localPlugins = pluginAppService.list();
        return localPlugins == null ? List.of() : localPlugins;
    }

    private List<PluginModuleDTO> installedPlugins() {
        List<PluginModuleDTO> localPlugins = pluginAppService.listInstalled();
        return localPlugins == null ? List.of() : localPlugins;
    }

    private void logOptionalDependencyWarnings(PluginStorePluginDescriptor descriptor, Installability installability) {
        for (PluginStorePluginDependency dependency : installability.unavailableOptionalDependencies()) {
            log.warn("Optional marketplace plugin dependency {} for {} is unavailable or incompatible; skipping installation",
                    dependency.code(), descriptor.code());
        }
    }

    private boolean matches(String range, String version) {
        if (range == null) {
            return true;
        }
        try {
            return SemVerRange.parse(range).matches(SemVer.parse(version));
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail storeDetail(String code) {
        return pluginStoreGateway.detail(normalizeCode(code))
                .orElseThrow(PluginStoreAppService::unavailable);
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw unavailable();
        }
        return code.trim();
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record ParsedStoreVersion(PluginStorePluginVersion storeVersion, SemVer version) {
    }

    private record Installability(boolean installable, String disabledReason,
                                  List<PluginStorePluginDependency> unavailableOptionalDependencies) {
    }

    private static BizException unavailable() {
        return new BizException("插件商店数据不可用");
    }

    private static BizException restartRequired() {
        return new BizException("插件或其已启用依赖方正在运行，需停止服务并重启后完成更新");
    }
}
