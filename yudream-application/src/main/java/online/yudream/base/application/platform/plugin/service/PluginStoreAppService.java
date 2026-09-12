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
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketSource;
import online.yudream.base.domain.platform.plugin.port.PluginStoreGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogVersion;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreStructuredVersion;
import online.yudream.base.domain.platform.plugin.valobj.SemVer;
import online.yudream.base.domain.platform.plugin.valobj.SemVerRange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件市场应用服务。远程源订阅、目录与安装不依赖插件市场源能力；
 * 本机 LOCAL 源仅在能力开启时进入目录。不回落 Nexus。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PluginStoreAppService {

    private final PluginStoreGateway pluginStoreGateway;
    private final PluginAppService pluginAppService;
    private final PluginMarketSourceAppService pluginMarketSourceAppService;

    @Value("${yudream.platform.plugin.upload-directory:plugins}")
    private String uploadDirectory;

    @Value("${yudream.platform.plugin.compatibility.host:1.0.0}")
    private String hostVersion = "1.0.0";

    @Value("${yudream.platform.plugin.compatibility.spi:2.6.0}")
    private String spiVersion = "2.6.0";

    @Value("${yudream.platform.plugin.compatibility.frontend-sdk:1.0.1}")
    private String frontendSdkVersion = "1.0.1";

    // ---------- 市场目录 ----------

    @Transactional(readOnly = true)
    public List<PluginStorePluginDTO> list() {
        Map<String, List<VersionRef>> view = catalogView();
        Map<String, String> originSources = installedOriginSources();
        List<PluginStorePluginInfo> infos = new ArrayList<>();
        for (Map.Entry<String, List<VersionRef>> entry : view.entrySet()) {
            VersionRef chosen = selectVersion(entry.getValue(), originSources.get(entry.getKey()));
            if (chosen == null) {
                continue;
            }
            PluginStorePluginDescriptor descriptor;
            try {
                descriptor = descriptorFor(chosen);
            } catch (BizException e) {
                log.warn("解析插件 {} 的市场 descriptor 失败：{}", entry.getKey(), e.getMessage());
                continue;
            }
            PluginStorePluginInfo info = new PluginStorePluginInfo();
            info.setCode(entry.getKey());
            info.setDescriptor(descriptor);
            if (chosen.source() != null) {
                info.setSourceCode(chosen.source().getCode());
                info.setSourceName(chosen.source().getName());
            }
            infos.add(info);
        }
        return infos.stream().map(PluginAssembler::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PluginStorePluginDetailDTO detail(String code) {
        String normalizedCode = normalizeCode(code);
        List<VersionRef> refs = catalogView().get(normalizedCode);
        if (refs == null || refs.isEmpty()) {
            throw unavailable();
        }
        List<PluginStorePluginVersion> versions = new ArrayList<>();
        Set<String> seenVersions = new LinkedHashSet<>();
        for (VersionRef ref : refs) {
            // refs 按源优先级排列，同版本保留优先级最高的一个
            if (!seenVersions.add(ref.releaseVersion())) {
                continue;
            }
            PluginStorePluginDescriptor descriptor;
            try {
                descriptor = descriptorFor(ref);
            } catch (BizException e) {
                log.warn("解析插件 {} 版本 {} 的 descriptor 失败：{}", normalizedCode, ref.releaseVersion(), e.getMessage());
                continue;
            }
            versions.add(new PluginStorePluginVersion(ref.releaseVersion(), descriptor,
                    sourceCode(ref), sourceName(ref)));
        }
        if (versions.isEmpty()) {
            throw unavailable();
        }
        List<PluginModuleDTO> localPlugins = versions.stream()
                .anyMatch(version -> !version.descriptor().dependencies().isEmpty()) ? localPlugins() : List.of();
        return PluginStorePluginDetailDTO.builder()
                .code(normalizedCode)
                .versions(versions.stream()
                        .map(version -> toDTO(version, evaluateInstallability(version.descriptor(), localPlugins)))
                        .toList())
                .build();
    }

    // ---------- 更新检查 ----------

    @Transactional(readOnly = true)
    public List<PluginMarketplaceUpdateDTO> updates() {
        List<PluginModuleDTO> localPlugins = installedPlugins();
        Map<String, List<VersionRef>> view = catalogView();
        Map<String, String> originSources = installedOriginSources();
        return localPlugins.stream()
                .filter(plugin -> validCode(plugin == null ? null : plugin.getCode()))
                .map(localPlugin -> latestUpdate(localPlugin, localPlugins, view, originSources))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private java.util.Optional<PluginMarketplaceUpdateDTO> latestUpdate(PluginModuleDTO localPlugin,
                                                                        List<PluginModuleDTO> localPlugins,
                                                                        Map<String, List<VersionRef>> view,
                                                                        Map<String, String> originSources) {
        if (!StringUtils.hasText(localPlugin.getCode())) {
            return java.util.Optional.empty();
        }
        List<VersionRef> refs = view.get(localPlugin.getCode());
        if (refs == null || refs.isEmpty()) {
            return java.util.Optional.empty();
        }
        VersionRef latest = selectVersion(refs, originSources.get(localPlugin.getCode()));
        if (latest == null) {
            return java.util.Optional.empty();
        }
        try {
            PluginStorePluginVersion latestVersion = new PluginStorePluginVersion(latest.releaseVersion(),
                    descriptorFor(latest), sourceCode(latest), sourceName(latest));
            return java.util.Optional.of(toUpdateDTO(localPlugin, latestVersion, localPlugins));
        } catch (BizException exception) {
            log.warn("解析插件 {} 的最新版本 descriptor 失败：{}", localPlugin.getCode(), exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<PluginMarketplaceUpdatePlanDTO> updatePlans() {
        List<PluginModuleDTO> localPlugins = installedPlugins();
        Map<String, List<VersionRef>> view = catalogView();
        Map<String, String> originSources = installedOriginSources();
        return localPlugins.stream()
                // 一个损坏的本地插件记录不能拖垮整个更新检查接口。
                .filter(plugin -> validCode(plugin == null ? null : plugin.getCode()))
                .map(plugin -> createUpdatePlan(plugin, localPlugins, view, originSources, null))
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
        PluginMarketplaceUpdatePlanDTO plan = createUpdatePlan(localPlugin, localPlugins, catalogView(),
                installedOriginSources(), targetVersion)
                .orElseThrow(PluginStoreAppService::unavailable);
        if (!isUpgrade(plan)) {
            throw unavailable();
        }
        return plan;
    }

    private java.util.Optional<PluginMarketplaceUpdatePlanDTO> createUpdatePlan(PluginModuleDTO localPlugin,
                                                                                List<PluginModuleDTO> localPlugins,
                                                                                Map<String, List<VersionRef>> view,
                                                                                Map<String, String> originSources,
                                                                                String targetVersion) {
        try {
            List<VersionRef> refs = view.get(normalizeCode(localPlugin.getCode()));
            if (refs == null || refs.isEmpty()) {
                return java.util.Optional.empty();
            }
            PluginStorePluginVersion target = selectTargetVersion(refs, originSources.get(localPlugin.getCode()), targetVersion);
            if (target == null) {
                return java.util.Optional.empty();
            }
            return buildUpdatePlan(localPlugin, localPlugins, target);
        } catch (BizException exception) {
            log.warn("跳过插件 {} 的市场更新计划：{}", localPlugin == null ? null : localPlugin.getCode(),
                    exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<PluginMarketplaceUpdatePlanDTO> buildUpdatePlan(PluginModuleDTO localPlugin,
                                                                               List<PluginModuleDTO> localPlugins,
                                                                               PluginStorePluginVersion target) {
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
    }

    private PluginStorePluginVersion selectTargetVersion(List<VersionRef> refs, String preferredSourceCode,
                                                         String targetVersion) {
        if (StringUtils.hasText(targetVersion)) {
            VersionRef ref = refs.stream()
                    .filter(item -> targetVersion.trim().equals(item.releaseVersion()))
                    .filter(item -> parseVersion(item.releaseVersion()).isPresent())
                    .findFirst()
                    .orElse(null);
            return ref == null ? null : new PluginStorePluginVersion(ref.releaseVersion(), descriptorFor(ref),
                    sourceCode(ref), sourceName(ref));
        }
        VersionRef ref = selectVersion(refs, preferredSourceCode);
        return ref == null ? null : new PluginStorePluginVersion(ref.releaseVersion(), descriptorFor(ref),
                sourceCode(ref), sourceName(ref));
    }

    // ---------- 回滚 ----------

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

    // ---------- 安装与更新 ----------

    @Transactional
    public PluginMarketplaceUpdateResultDTO update(String code, String targetVersion, String sourceCode) {
        // 更新会同时替换 JAR、写回备份元数据并刷新插件注册表，同一进程内必须串行。
        synchronized (this) {
            return updateSerial(code, targetVersion, sourceCode);
        }
    }

    private PluginMarketplaceUpdateResultDTO updateSerial(String code, String targetVersion, String sourceCode) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(targetVersion)) {
            throw unavailable();
        }
        List<PluginModuleDTO> localPlugins = installedPlugins();
        PluginModuleDTO localPlugin = localPlugins.stream()
                .filter(plugin -> normalizedCode.equals(plugin.getCode()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        List<VersionRef> refs = catalogView().get(normalizedCode);
        if (refs == null || refs.isEmpty()) {
            throw unavailable();
        }
        VersionRef target = selectTargetRef(restrictBySource(refs, sourceCode), targetVersion);
        if (target == null) {
            throw unavailable();
        }
        PluginMarketplaceUpdatePlanDTO plan = buildUpdatePlan(localPlugin, localPlugins,
                new PluginStorePluginVersion(target.releaseVersion(), descriptorFor(target),
                        sourceCode(target), sourceName(target)))
                .orElseThrow(PluginStoreAppService::unavailable);
        if (!isUpgrade(plan) || StringUtils.hasText(plan.getBlockedReason())) {
            throw unavailable();
        }
        return PluginMarketplaceUpdateResultDTO.builder()
                .modules(downloadStoreVersion(target, descriptorFor(target), true))
                .requiresRestart(true)
                .build();
    }

    @Transactional
    public List<PluginModuleDTO> install(String code, String version, String sourceCode) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(version)) {
            throw unavailable();
        }
        List<VersionRef> refs = catalogView().get(normalizedCode);
        if (refs == null || refs.isEmpty()) {
            throw unavailable();
        }
        VersionRef ref = restrictBySource(refs, sourceCode).stream()
                .filter(item -> version.trim().equals(item.releaseVersion()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        return installStoreVersion(ref, descriptorFor(ref));
    }

    private List<VersionRef> restrictBySource(List<VersionRef> refs, String sourceCode) {
        if (!StringUtils.hasText(sourceCode)) {
            return refs;
        }
        return refs.stream()
                .filter(ref -> ref.source() != null && sourceCode.equals(ref.source().getCode()))
                .toList();
    }

    private VersionRef selectTargetRef(List<VersionRef> refs, String targetVersion) {
        return refs.stream()
                .filter(item -> targetVersion.trim().equals(item.releaseVersion()))
                .filter(item -> parseVersion(item.releaseVersion()).isPresent())
                .findFirst()
                .orElse(null);
    }

    private List<PluginModuleDTO> installStoreVersion(VersionRef ref, PluginStorePluginDescriptor descriptor) {
        List<PluginModuleDTO> localPlugins = descriptor.dependencies().isEmpty() ? List.of() : localPlugins();
        Installability installability = evaluateInstallability(descriptor, localPlugins);
        if (!installability.installable()) {
            throw unavailable();
        }
        logOptionalDependencyWarnings(descriptor, installability);
        return downloadStoreVersion(ref, descriptor, false);
    }

    private List<PluginModuleDTO> downloadStoreVersion(VersionRef ref, PluginStorePluginDescriptor descriptor, boolean update) {
        Path stagedJar = null;
        try {
            Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            stagedJar = Files.createTempFile(directory, ".plugin-store-", ".tmp");
            if (ref.structured() != null && StringUtils.hasText(ref.structured().downloadUrl())
                    && ref.structured().downloadUrl().startsWith("local:")) {
                Path sourceJar = pluginMarketSourceAppService.resolveLocalJar(ref.structured().downloadUrl());
                Files.copy(sourceJar, stagedJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (StringUtils.hasText(descriptor.jar().sha256())) {
                    verifySha256(stagedJar, descriptor.jar().sha256());
                }
            } else {
                pluginStoreGateway.downloadJar(refOf(ref.source()), descriptor, stagedJar);
            }
            List<PluginModuleDTO> result = update
                    ? pluginAppService.updateStoreJar(stagedJar, descriptor.code(), descriptor.version(),
                            descriptor.main(), sourceCode(ref))
                    : pluginAppService.installStoreJar(stagedJar, descriptor.code(), descriptor.version(),
                            descriptor.main(), sourceCode(ref));
            stagedJar = null;
            return result;
        } catch (IOException e) {
            throw new BizException("插件 JAR 下载失败：" + e.getMessage());
        } finally {
            deleteQuietly(stagedJar);
        }
    }

    // ---------- 多源目录视图 ----------

    /** code -> 该插件在各启用源上的全部版本引用；LOCAL 源仅在能力开启时出现。 */
    private Map<String, List<VersionRef>> catalogView() {
        Map<String, List<VersionRef>> view = new LinkedHashMap<>();
        for (PluginMarketSourceAppService.SourceCatalog catalog : pluginMarketSourceAppService.enabledSourceCatalogs()) {
            putIntoView(view, catalog.snapshot().entries(), catalog.source());
        }
        return view;
    }

    private void putIntoView(Map<String, List<VersionRef>> view, List<PluginStoreCatalogEntry> entries,
                             PluginMarketSource source) {
        for (PluginStoreCatalogEntry entry : entries) {
            List<VersionRef> refs = view.computeIfAbsent(entry.code(), key -> new ArrayList<>());
            if (entry.structuredVersions() != null && !entry.structuredVersions().isEmpty()) {
                for (PluginStoreStructuredVersion version : entry.structuredVersions()) {
                    refs.add(new VersionRef(source, entry, version.releaseVersion(), null, version));
                }
                continue;
            }
            for (PluginStoreCatalogVersion version : entry.versions()) {
                refs.add(new VersionRef(source, entry, version.releaseVersion(), version.descriptorUrl(), null));
            }
        }
    }

    /**
     * 选目标版本：全部候选里取最高 SemVer；同版本按「安装来源 > 源优先级顺序」取胜。
     * refs 本身已按源优先级排列，首见即保底。
     */
    private VersionRef selectVersion(List<VersionRef> refs, String preferredSourceCode) {
        VersionRef best = null;
        SemVer bestVersion = null;
        for (VersionRef ref : refs) {
            SemVer version;
            try {
                version = SemVer.parse(ref.releaseVersion());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (best == null || version.compareTo(bestVersion) > 0) {
                best = ref;
                bestVersion = version;
                continue;
            }
            if (version.compareTo(bestVersion) == 0 && sourceRank(ref, preferredSourceCode) < sourceRank(best, preferredSourceCode)) {
                best = ref;
            }
        }
        return best;
    }

    private int sourceRank(VersionRef ref, String preferredSourceCode) {
        String code = sourceCode(ref);
        if (preferredSourceCode != null && preferredSourceCode.equals(code)) {
            return 0;
        }
        return code == null ? 1 : 2;
    }

    /** 最新版本直接解析快照保存的 descriptor 原文（零外呼），LOCAL/V2 走结构化字段；历史版本按需从源拉取。 */
    private PluginStorePluginDescriptor descriptorFor(VersionRef ref) {
        if (ref.structured() != null) {
            return descriptorFromStructured(ref);
        }
        List<PluginStoreCatalogVersion> versions = ref.entry().versions();
        if (!versions.isEmpty()) {
            PluginStoreCatalogVersion latest = versions.get(versions.size() - 1);
            if (latest.releaseVersion().equals(ref.releaseVersion()) && latest.descriptorUrl().equals(ref.descriptorUrl())) {
                return pluginStoreGateway.parseDescriptor(refOf(ref.source()), ref.entry().indexUrl(),
                        ref.entry().latestDescriptorJson());
            }
        }
        return pluginStoreGateway.fetchDescriptor(refOf(ref.source()), ref.entry().indexUrl(), ref.descriptorUrl());
    }

    private PluginStorePluginDescriptor descriptorFromStructured(VersionRef ref) {
        PluginStoreStructuredVersion version = ref.structured();
        PluginStorePluginCompatibility compatibility = null;
        if (version.compatibility() != null && !version.compatibility().isEmpty()) {
            compatibility = new PluginStorePluginCompatibility(
                    version.compatibility().get("host"),
                    version.compatibility().get("spi"),
                    version.compatibility().get("frontendSdk"));
        }
        return new PluginStorePluginDescriptor(
                version.releaseVersion(),
                ref.entry().code(),
                version.releaseVersion(),
                version.main(),
                version.displayName(),
                version.description(),
                null,
                List.of(),
                compatibility,
                version.dependencies(),
                new PluginStorePluginJar(
                        "self-hosted:" + ref.entry().code() + ":" + version.releaseVersion(),
                        version.downloadUrl(),
                        version.sha256()));
    }

    private PluginStoreSourceRef refOf(PluginMarketSource source) {
        if (source == null) {
            throw unavailable();
        }
        return pluginMarketSourceAppService.sourceRef(source);
    }

    private String sourceCode(VersionRef ref) {
        return ref.source() == null ? null : ref.source().getCode();
    }

    private String sourceName(VersionRef ref) {
        return ref.source() == null ? null : ref.source().getName();
    }

    private Map<String, String> installedOriginSources() {
        Map<String, String> origin = new HashMap<>();
        for (PluginModuleDTO plugin : installedPlugins()) {
            if (StringUtils.hasText(plugin.getMarketSourceCode())) {
                origin.put(plugin.getCode(), plugin.getMarketSourceCode());
            }
        }
        return origin;
    }

    // ---------- 安装性与通用校验 ----------

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

    private java.util.Optional<SemVer> parseVersion(String releaseVersion) {
        try {
            return java.util.Optional.of(SemVer.parse(releaseVersion));
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

    private String normalizeCode(String code) {
        if (!validCode(code)) {
            throw unavailable();
        }
        return code.trim();
    }

    private boolean validCode(String code) {
        return StringUtils.hasText(code) && code.trim().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
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

    private void verifySha256(Path file, String expected) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actual = java.util.HexFormat.of().formatHex(digest.digest());
            if (!expected.equalsIgnoreCase(actual)) {
                throw unavailable();
            }
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new BizException("插件 JAR 下载失败：" + e.getMessage());
        }
    }

    private record VersionRef(PluginMarketSource source, PluginStoreCatalogEntry entry,
                              String releaseVersion, String descriptorUrl, PluginStoreStructuredVersion structured) {
    }

    private record Installability(boolean installable, String disabledReason,
                                  List<PluginStorePluginDependency> unavailableOptionalDependencies) {
    }

    private static BizException unavailable() {
        return new BizException("插件商店数据不可用");
    }
}
