package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketplaceBatchInstallCmd;
import online.yudream.base.application.platform.plugin.dto.PluginDependencyStatusDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceInstallPlanDTO;
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
        return rollbackLocal(normalizedCode, null, false);
    }

    @Transactional
    public PluginMarketplaceUpdateResultDTO rollback(String code, String targetVersion) {
        return rollback(code, targetVersion, false);
    }

    /** 级联回滚：运行中也可回滚，停机/恢复由插件应用服务按依赖顺序处理。 */
    @Transactional
    public PluginMarketplaceUpdateResultDTO rollback(String code, String targetVersion, boolean cascade) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(targetVersion)) {
            throw unavailable();
        }
        return rollbackLocal(normalizedCode, targetVersion.trim(), cascade);
    }

    private PluginMarketplaceUpdateResultDTO rollbackLocal(String normalizedCode, String targetVersion, boolean cascade) {
        boolean installed = installedPlugins().stream()
                .anyMatch(plugin -> normalizedCode.equals(plugin.getCode()));
        if (!installed) {
            throw unavailable();
        }
        List<PluginModuleDTO> modules = pluginAppService.rollbackStoreJar(normalizedCode, targetVersion, cascade);
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

    // ---------- 安装计划与批量安装 ----------

    /**
     * 安装计划：目标版本的直接/传递前置依赖清单（被依赖者在前），每项标注
     * 已安装/版本满足/市场候选版本与候选自身可安装性，供安装确认弹窗勾选后批量下载。
     */
    @Transactional(readOnly = true)
    public PluginMarketplaceInstallPlanDTO installPlan(String code, String releaseVersion, String sourceCode) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(releaseVersion)) {
            throw unavailable();
        }
        Map<String, List<VersionRef>> view = catalogView();
        List<VersionRef> refs = view.get(normalizedCode);
        if (refs == null || refs.isEmpty()) {
            throw unavailable();
        }
        VersionRef target = restrictBySource(refs, sourceCode).stream()
                .filter(item -> releaseVersion.trim().equals(item.releaseVersion()))
                .findFirst()
                .orElseThrow(PluginStoreAppService::unavailable);
        PluginStorePluginDescriptor targetDescriptor = descriptorFor(target);
        List<PluginModuleDTO> localPlugins = installedPlugins();
        Installability installability = evaluateInstallability(targetDescriptor, localPlugins);
        List<PluginMarketplaceInstallPlanDTO.EntryDTO> entries = new ArrayList<>();
        collectInstallEntries(targetDescriptor, view, installedOriginSources(), localPlugins,
                new LinkedHashSet<>(), entries);
        return PluginMarketplaceInstallPlanDTO.builder()
                .code(normalizedCode)
                .releaseVersion(target.releaseVersion())
                .installable(installability.installable())
                .installDisabledReason(installability.disabledReason())
                .entries(entries)
                .build();
    }

    /**
     * 递归收集安装计划条目（后序：被依赖者先于消费方输出）。硬依赖未满足且市场有可安装
     * 候选时继续下钻其硬依赖；软依赖只评估候选自身可安装性，不下钻其依赖。
     */
    private void collectInstallEntries(PluginStorePluginDescriptor descriptor,
                                       Map<String, List<VersionRef>> view, Map<String, String> originSources,
                                       List<PluginModuleDTO> localPlugins, Set<String> visiting,
                                       List<PluginMarketplaceInstallPlanDTO.EntryDTO> entries) {
        for (PluginStorePluginDependency dependency : descriptor.dependencies()) {
            String dependencyCode = dependency.code();
            if (!validCode(dependencyCode) || !visiting.add(dependencyCode)) {
                continue;
            }
            try {
                PluginMarketplaceInstallPlanDTO.EntryDTO entry = buildInstallPlanEntry(dependencyCode,
                        dependency.required(), dependency.range(), view, originSources, localPlugins);
                boolean satisfied = entry.isInstalled() && entry.isVersionSatisfied();
                VersionRef candidateRef = null;
                if (dependency.required() && !satisfied && entry.isStoreAvailable() && entry.isInstallable()
                        && StringUtils.hasText(entry.getStoreVersion())) {
                    candidateRef = findRef(view, dependencyCode, entry.getStoreVersion(), entry.getStoreSourceCode());
                }
                if (candidateRef != null) {
                    collectInstallEntries(descriptorFor(candidateRef), view, originSources, localPlugins,
                            visiting, entries);
                }
                mergeInstallPlanEntry(entries, entry);
            } catch (BizException e) {
                log.warn("解析安装计划依赖 {} 失败：{}", dependencyCode, e.getMessage());
            } finally {
                visiting.remove(dependencyCode);
            }
        }
    }

    /** 同一依赖出现在多条链上时合并：保留必须语义与更完整的市场候选信息。 */
    private void mergeInstallPlanEntry(List<PluginMarketplaceInstallPlanDTO.EntryDTO> entries,
                                       PluginMarketplaceInstallPlanDTO.EntryDTO entry) {
        for (int i = 0; i < entries.size(); i++) {
            PluginMarketplaceInstallPlanDTO.EntryDTO existing = entries.get(i);
            if (!existing.getCode().equals(entry.getCode())) {
                continue;
            }
            boolean required = existing.isRequired() || entry.isRequired();
            boolean installable = existing.isInstallable() && entry.isInstallable();
            String disabledReason = existing.getInstallDisabledReason() == null
                    ? entry.getInstallDisabledReason() : existing.getInstallDisabledReason();
            entries.set(i, existing.toBuilder()
                    .required(required)
                    .installable(installable)
                    .installDisabledReason(disabledReason)
                    .build());
            return;
        }
        entries.add(entry);
    }

    private PluginMarketplaceInstallPlanDTO.EntryDTO buildInstallPlanEntry(String dependencyCode, boolean required,
                                                                           String range,
                                                                           Map<String, List<VersionRef>> view,
                                                                           Map<String, String> originSources,
                                                                           List<PluginModuleDTO> localPlugins) {
        PluginModuleDTO local = localPlugins.stream()
                .filter(plugin -> dependencyCode.equals(plugin.getCode()))
                .findFirst()
                .orElse(null);
        boolean installed = local != null;
        boolean versionSatisfied = installed && matchesQuietly(range, local.getVersion());
        List<VersionRef> refs = view.get(dependencyCode);
        boolean storeAvailable = refs != null && !refs.isEmpty();
        VersionRef best = null;
        VersionRef bestInRange = null;
        SemVer bestVersion = null;
        SemVer bestInRangeVersion = null;
        if (storeAvailable) {
            for (VersionRef ref : refs) {
                SemVer version = parseVersion(ref.releaseVersion()).orElse(null);
                if (version == null) {
                    continue;
                }
                if (bestVersion == null || version.compareTo(bestVersion) > 0) {
                    best = ref;
                    bestVersion = version;
                }
                if (matchesQuietly(range, ref.releaseVersion())
                        && (bestInRangeVersion == null || version.compareTo(bestInRangeVersion) > 0)) {
                    bestInRange = ref;
                    bestInRangeVersion = version;
                }
            }
        }
        // 范围内优先，其次市场最高版本（版本约束最终以安装时校验为准）
        VersionRef chosen = bestInRange != null ? bestInRange : best;
        String displayName = installed && StringUtils.hasText(local.getName()) ? local.getName() : dependencyCode;
        boolean entryInstallable = true;
        String disabledReason = null;
        String storeVersion = null;
        String storeSourceCode = null;
        String storeSourceName = null;
        if (chosen != null) {
            storeVersion = chosen.releaseVersion();
            storeSourceCode = sourceCode(chosen);
            storeSourceName = sourceName(chosen);
            try {
                PluginStorePluginDescriptor candidateDescriptor = descriptorFor(chosen);
                if (displayName.equals(dependencyCode) && StringUtils.hasText(candidateDescriptor.displayName())) {
                    displayName = candidateDescriptor.displayName();
                }
                Installability candidateInstallability = evaluateInstallability(candidateDescriptor, localPlugins);
                entryInstallable = candidateInstallability.installable();
                disabledReason = candidateInstallability.disabledReason();
            } catch (BizException e) {
                entryInstallable = false;
                disabledReason = "市场版本数据不可用";
            }
        } else if (storeAvailable && StringUtils.hasText(range)) {
            entryInstallable = false;
            disabledReason = "市场版本不满足版本要求 " + range;
        }
        if (required && !installed && !storeAvailable) {
            entryInstallable = false;
            disabledReason = "必需依赖不在任何已启用市场源中";
        }
        return PluginMarketplaceInstallPlanDTO.EntryDTO.builder()
                .code(dependencyCode)
                .displayName(displayName)
                .required(required)
                .range(range)
                .installed(installed)
                .installedVersion(installed ? local.getVersion() : null)
                .versionSatisfied(versionSatisfied)
                .storeAvailable(storeAvailable)
                .storeVersion(storeVersion)
                .storeSourceCode(storeSourceCode)
                .storeSourceName(storeSourceName)
                .installable(entryInstallable)
                .installDisabledReason(disabledReason)
                .build();
    }

    private VersionRef findRef(Map<String, List<VersionRef>> view, String code, String releaseVersion, String sourceCode) {
        List<VersionRef> refs = view.get(code);
        if (refs == null) {
            return null;
        }
        return refs.stream()
                .filter(ref -> releaseVersion.equals(ref.releaseVersion()))
                .filter(ref -> !StringUtils.hasText(sourceCode) || sourceCode.equals(sourceCode(ref)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 批量安装：按传入顺序（依赖必须排在消费方之前）逐项安装，任一项失败整体回滚。
     * 与单插件安装/更新共用串行锁，避免并发替换 JAR 与注册表。
     */
    @Transactional
    public List<PluginModuleDTO> installBatch(PluginMarketplaceBatchInstallCmd cmd) {
        if (cmd == null || cmd.getItems() == null || cmd.getItems().isEmpty()) {
            throw new BizException("安装清单不能为空");
        }
        boolean malformed = cmd.getItems().stream().anyMatch(item -> item == null
                || !StringUtils.hasText(item.getCode()) || !StringUtils.hasText(item.getReleaseVersion()));
        if (malformed) {
            throw new BizException("安装清单包含缺少编码或版本的条目");
        }
        synchronized (this) {
            List<PluginModuleDTO> modules = List.of();
            for (PluginMarketplaceBatchInstallCmd.Item item : cmd.getItems()) {
                try {
                    modules = install(item.getCode(), item.getReleaseVersion(), item.getSourceCode());
                } catch (BizException e) {
                    throw new BizException("批量安装 " + item.getCode() + "@" + item.getReleaseVersion()
                            + " 失败：" + e.getMessage() + "；本次批量安装已整体回滚");
                }
            }
            return modules;
        }
    }

    // ---------- 启用依赖状态 ----------

    /**
     * 已安装插件的前置依赖状态：硬/软依赖逐项标注安装、启用与市场候选版本，
     * 供启用前依赖预览弹窗（硬缺失引导安装、软依赖可选启用）。
     */
    @Transactional(readOnly = true)
    public PluginDependencyStatusDTO dependencyStatus(String code) {
        String normalizedCode = normalizeCode(code);
        List<PluginModuleDTO> localPlugins = installedPlugins();
        PluginModuleDTO target = localPlugins.stream()
                .filter(plugin -> normalizedCode.equals(plugin.getCode()))
                .findFirst()
                .orElseThrow(() -> new BizException("插件不存在：" + normalizedCode));
        Map<String, List<VersionRef>> view = catalogView();
        Map<String, String> originSources = installedOriginSources();
        List<PluginDependencyStatusDTO.DependencyDTO> dependencies = new ArrayList<>();
        for (String dependencyCode : concatDistinct(target.getDependencies(), target.getSoftDependencies())) {
            boolean required = target.getDependencies() != null && target.getDependencies().contains(dependencyCode);
            PluginModuleDTO local = localPlugins.stream()
                    .filter(plugin -> dependencyCode.equals(plugin.getCode()))
                    .findFirst()
                    .orElse(null);
            boolean installed = local != null;
            VersionRef chosen = storeCandidate(view, dependencyCode, originSources.get(dependencyCode));
            String name = installed && StringUtils.hasText(local.getName()) ? local.getName() : dependencyCode;
            String storeVersion = null;
            String storeSourceCode = null;
            String storeSourceName = null;
            if (chosen != null) {
                storeVersion = chosen.releaseVersion();
                storeSourceCode = sourceCode(chosen);
                storeSourceName = sourceName(chosen);
                if (name.equals(dependencyCode)) {
                    try {
                        PluginStorePluginDescriptor candidateDescriptor = descriptorFor(chosen);
                        if (StringUtils.hasText(candidateDescriptor.displayName())) {
                            name = candidateDescriptor.displayName();
                        }
                    } catch (BizException ignored) {
                        // 展示名解析失败退回编码
                    }
                }
            }
            dependencies.add(PluginDependencyStatusDTO.DependencyDTO.builder()
                    .code(dependencyCode)
                    .name(name)
                    .required(required)
                    .installed(installed)
                    .installedVersion(installed ? local.getVersion() : null)
                    .loaded(installed && local.isLoaded())
                    .enabled(installed && local.isEnabled())
                    .storeAvailable(chosen != null)
                    .storeVersion(storeVersion)
                    .storeSourceCode(storeSourceCode)
                    .storeSourceName(storeSourceName)
                    .build());
        }
        return PluginDependencyStatusDTO.builder()
                .code(normalizedCode)
                .dependencies(dependencies)
                .build();
    }

    private List<String> concatDistinct(List<String> dependencies, List<String> softDependencies) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (dependencies != null) {
            dependencies.stream().filter(StringUtils::hasText).map(String::trim).forEach(codes::add);
        }
        if (softDependencies != null) {
            softDependencies.stream().filter(StringUtils::hasText).map(String::trim).forEach(codes::add);
        }
        return new ArrayList<>(codes);
    }

    /** 依赖的市场候选：优先安装来源源，其次全局最高版本。 */
    private VersionRef storeCandidate(Map<String, List<VersionRef>> view, String code, String originSourceCode) {
        List<VersionRef> refs = view.get(code);
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        return selectVersion(refs, originSourceCode);
    }

    private boolean matchesQuietly(String range, String version) {
        if (range == null) {
            return true;
        }
        try {
            return SemVerRange.parse(range).matches(SemVer.parse(version));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<PluginModuleDTO> installStoreVersion(VersionRef ref, PluginStorePluginDescriptor descriptor) {
        List<PluginModuleDTO> localPlugins = descriptor.dependencies().isEmpty() ? List.of() : localPlugins();
        Installability installability = evaluateInstallability(descriptor, localPlugins);
        if (!installability.installable()) {
            throw new BizException(StringUtils.hasText(installability.disabledReason())
                    ? installability.disabledReason() : "插件商店数据不可用");
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
                null,
                null,
                null,
                null,
                compatibility,
                version.dependencies(),
                new PluginStorePluginJar(
                        "self-hosted:" + ref.entry().code() + ":" + version.releaseVersion(),
                        version.downloadUrl(),
                        version.sha256()),
                version.category(),
                version.tags(),
                version.gitUrl());
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
