package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendAssetDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpEndpointDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.menu.enumerate.SeedSyncMode;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAppService {

    private static final String LEGACY_SKIN_PLUGIN_CODE = "blessing-skin";
    private static final String YUDREAM_SKIN_PLUGIN_CODE = "yudream-skin";
    private static final int MENU_CLEANUP_ATTEMPTS = 3;

    private final PluginModuleRepo pluginModuleRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final PermissionDomainService permissionDomainService;
    private final PluginMenuProjectionService pluginMenuProjectionService;
    private final RoleRepo roleRepo;

    @Value("${yudream.platform.plugin.upload-directory:plugins}")
    private String uploadDirectory;

    @Value("${yudream.system.seed.menu.sync-mode:MISSING_ONLY}")
    private SeedSyncMode menuSeedSyncMode = SeedSyncMode.MISSING_ONLY;

    @Transactional
    public List<PluginModuleDTO> list() {
        syncPluginRegistry();
        List<PluginModule> modules = pluginModuleRepo.findAll();
        reconcileRuntimeHealth(modules);
        return modules.stream()
                .sorted(Comparator.comparing(PluginModule::getCode))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<PluginModuleDTO> refresh() {
        return list();
    }

    @Transactional(readOnly = true)
    public List<PluginModuleDTO> listInstalled() {
        return pluginModuleRepo.findAll().stream()
                .sorted(Comparator.comparing(PluginModule::getCode))
                .map(module -> PluginAssembler.toDTO(module, false, false))
                .toList();
    }

    @Transactional
    public List<PluginModuleDTO> upload(InputStream inputStream, String originalFilename, long size) {
        if (size <= 0) {
            throw new BizException("插件 JAR 不能为空");
        }
        validateJarFilename(originalFilename);
        Path directory = uploadDirectory();
        Path tempFile = null;
        try {
            Files.createDirectories(directory);
            tempFile = Files.createTempFile(directory, ".plugin-upload-", ".tmp");
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            List<PluginModuleDTO> result = installStagedJar(tempFile, null, null, null);
            tempFile = null;
            return result;
        } catch (IOException e) {
            throw new BizException("插件 JAR 上传失败：" + e.getMessage());
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Transactional
    public List<PluginModuleDTO> installStoreJar(Path stagedJar, String expectedCode, String expectedVersion, String expectedMain) {
        PluginDescriptorInfo descriptor = pluginRuntimeGateway.describe(stagedJar)
                .orElseThrow(() -> new BizException("上传文件不是有效的 YuDream 插件 JAR"));
        validateStoreDescriptor(descriptor, expectedCode, expectedVersion, expectedMain);
        return installMarketplaceJar(stagedJar, descriptor);
    }

    @Transactional
    public List<PluginModuleDTO> updateStoreJar(Path stagedJar, String expectedCode, String expectedVersion, String expectedMain) {
        return installStoreJar(stagedJar, expectedCode, expectedVersion, expectedMain);
    }

    @Transactional
    public List<PluginModuleDTO> rollbackStoreJar(String code) {
        return rollbackStoreJar(code, null);
    }

    @Transactional
    public List<PluginModuleDTO> rollbackStoreJar(String code, String expectedVersion) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("插件代码不能为空");
        }
        PluginModule module = module(code.trim());
        rejectRollbackWhenRunning(module);
        Path active = activeJarPath(module);
        Path backup = backupJarPath(module);
        validateBackup(module, backup, expectedVersion);
        PluginModuleSnapshot originalMetadata = snapshot(module);
        Path activeOriginal = null;
        Path backupOriginal = null;
        Path activeSwap = null;
        Path backupSwap = null;
        boolean switchStarted = false;
        boolean recoverySucceeded = false;
        try {
            Files.createDirectories(backupDirectory());
            activeOriginal = stageJar(active, active.getParent(), ".plugin-rollback-active-original-");
            backupOriginal = stageJar(backup, active.getParent(), ".plugin-rollback-backup-original-");
            activeSwap = stageJar(backup, active.getParent(), ".plugin-rollback-active-");
            backupSwap = stageJar(active, active.getParent(), ".plugin-rollback-backup-");
            validateJarAgainstDescriptor(activeSwap, module, expectedVersion, true);
            validateJarAgainstDescriptor(backupSwap, module, null, false);
            switchStarted = true;
            moveReplacing(activeSwap, active);
            moveReplacing(backupSwap, backup);
            swapBackupMetadata(module, backup);
            pluginModuleRepo.save(module);
            syncPluginRegistry();
            recoverySucceeded = true;
            return modules();
        } catch (Exception exception) {
            boolean activeRestored = !switchStarted || restoreActiveFromStaged(active, activeOriginal);
            boolean backupRestored = !switchStarted || restoreBackupFromActive(backup, backupOriginal);
            if (switchStarted && (!activeRestored || !backupRestored)) {
                log.error("Rollback recovery incomplete for {} (activeRestored={}, backupRestored={}); retaining original staging files",
                        module.getCode(), activeRestored, backupRestored);
            }
            restoreSnapshot(module, originalMetadata);
            try {
                pluginModuleRepo.save(module);
            } catch (Exception metadataFailure) {
                log.error("Failed to restore plugin rollback metadata for {}", module.getCode(), metadataFailure);
            }
            throw new BizException("插件回滚失败：" + rootMessage(exception)
                    + (switchStarted && (!activeRestored || !backupRestored) ? "；文件恢复未完成，已保留恢复副本" : ""));
        } finally {
            if (recoverySucceeded) {
                deleteQuietly(activeOriginal);
                deleteQuietly(backupOriginal);
            }
            deleteQuietly(activeSwap);
            deleteQuietly(backupSwap);
        }
    }

    private List<PluginModuleDTO> installStagedJar(Path stagedJar, String expectedCode, String expectedVersion, String expectedMain) {
        PluginDescriptorInfo descriptor = pluginRuntimeGateway.describe(stagedJar)
                .orElseThrow(() -> new BizException("上传文件不是有效的 YuDream 插件 JAR"));
        validateStoreDescriptor(descriptor, expectedCode, expectedVersion, expectedMain);
        PluginModule existing = pluginModuleRepo.findByCode(descriptor.code()).orElse(null);
        Path target = targetJarPath(uploadDirectory(), descriptorJarFilename(descriptor), existing);
        try {
            stopExistingPlugin(existing);
            moveUploadedJar(stagedJar, target);
            deleteOldJarIfChanged(existing, target);
            syncPluginRegistry();
            return modules();
        } catch (IOException e) {
            throw new BizException("插件 JAR 上传失败：" + e.getMessage());
        }
    }

    private List<PluginModuleDTO> installMarketplaceJar(Path stagedJar, PluginDescriptorInfo descriptor) {
        PluginModule existing = pluginModuleRepo.findByCode(descriptor.code()).orElse(null);
        if (existing == null || !StringUtils.hasText(existing.getJarPath())) {
            return installStagedJar(stagedJar, descriptor.code(), descriptor.version(), descriptor.mainClass());
        }
        Path active = activeJarPath(existing);
        Path backup = controlledBackupPath(existing, active);
        Map<String, PluginModule> modules = modulesByCode();
        List<PluginModule> affected = affectedModules(existing, modules);
        Map<String, PluginModuleSnapshot> originalMetadata = affected.stream()
                .collect(Collectors.toMap(PluginModule::getCode, this::snapshot));
        Path activeOriginal = null;
        Path backupOriginal = null;
        Path actualBackup = null;
        boolean backupExisted = Files.isRegularFile(backup);
        boolean switchStarted = false;
        try {
            Files.createDirectories(backup.getParent());
            activeOriginal = stageJar(active, active.getParent(), ".plugin-marketplace-active-original-");
            if (backupExisted) {
                backupOriginal = stageJar(backup, backup.getParent(), ".plugin-marketplace-backup-original-");
            }
            stopAffectedForMarketplaceUpdate(affected);
            switchStarted = true;
            // stopAffectedForMarketplaceUpdate 已经保存过 affected 中的同一模块，
            // 这里重新读取以获得最新 @Version，避免 backupActiveJar 用陈旧版本再次保存触发乐观锁冲突。
            existing = pluginModuleRepo.findByCode(existing.getCode()).orElse(existing);
            actualBackup = backupActiveJar(existing, active, backup);
            moveUploadedJar(stagedJar, active);
            syncPluginRegistry();
            return modules();
        } catch (Exception exception) {
            boolean activeRestored = !switchStarted || restoreActiveFromStaged(active, activeOriginal);
            boolean backupRestored = restoreMarketplaceBackup(backup, actualBackup, backupOriginal, backupExisted);
            restoreMarketplaceMetadata(affected, originalMetadata);
            if (!activeRestored || !backupRestored) {
                log.error("Marketplace update recovery incomplete for {} (activeRestored={}, backupRestored={})", existing.getCode(), activeRestored, backupRestored);
            }
            throw new BizException("插件市场更新失败：" + rootMessage(exception)
                    + (!activeRestored || !backupRestored ? "；文件恢复未完成，已保留恢复副本" : ""));
        } finally {
            deleteQuietly(activeOriginal);
            deleteQuietly(backupOriginal);
        }
    }

    private void validateStoreDescriptor(PluginDescriptorInfo descriptor, String expectedCode, String expectedVersion, String expectedMain) {
        if (expectedCode != null && !expectedCode.equals(descriptor.code())
                || expectedVersion != null && !expectedVersion.equals(descriptor.version())
                || expectedMain != null && !expectedMain.equals(descriptor.mainClass())) {
            throw new BizException("插件 JAR 描述信息与商店版本不一致");
        }
    }

    @Transactional
    public PluginModuleDTO load(String code) {
        PluginModule module = module(code);
        try {
            pluginRuntimeGateway.load(module);
            module.markLoaded();
            return toDTO(pluginModuleRepo.save(module));
        } catch (Exception e) {
            markError(module, e);
            throw new BizException("插件加载失败：" + rootMessage(e));
        }
    }

    @Transactional(noRollbackFor = BizException.class)
    public PluginModuleDTO enable(String code) {
        Map<String, PluginModule> modules = modulesByCode();
        PluginModule module = modules.get(code);
        if (module == null) {
            throw new BizException("插件不存在，请先刷新插件目录");
        }
        try {
            PluginModule enabled = enableRuntimeWithDependencies(module, modules, new HashSet<>(), new HashSet<>());
            enabled.setRestoreIntentActive(true);
            return toDTO(pluginModuleRepo.save(enabled));
        } catch (Exception e) {
            String failure = rootMessage(e);
            String cleanupFailure = cleanupFailedEnable(module.getCode());
            markError(module, appendCleanupFailure(failure, cleanupFailure));
            throw new BizException("插件启用失败：" + failure);
        }
    }

    @Transactional(noRollbackFor = BizException.class)
    public PluginModuleDTO disable(String code) {
        PluginModule module = module(code);
        RuntimeException runtimeFailure = null;
        try {
            pluginRuntimeGateway.disable(code);
        } catch (RuntimeException e) {
            runtimeFailure = e;
        }
        module.markDisabled();
        module.setRestoreIntentActive(false);
        PluginModule saved = pluginModuleRepo.save(module);
        String menuFailure = reconcileUnavailableMenus(code);
        if (runtimeFailure != null || menuFailure != null) {
            String failure = runtimeFailure == null ? menuFailure : rootMessage(runtimeFailure);
            throw new BizException("插件禁用失败：" + appendCleanupFailure(failure, runtimeFailure == null ? null : menuFailure));
        }
        return toDTO(saved);
    }

    @Transactional(noRollbackFor = BizException.class)
    public PluginModuleDTO unload(String code) {
        PluginModule module = module(code);
        pluginRuntimeGateway.unload(code);
        module.markUnloaded();
        module.setRestoreIntentActive(false);
        PluginModule saved = pluginModuleRepo.save(module);
        String menuFailure = reconcileUnavailableMenus(code);
        if (menuFailure != null) {
            throw new BizException("插件卸载失败：" + menuFailure);
        }
        return toDTO(saved);
    }

    @Transactional(noRollbackFor = BizException.class)
    public void delete(String code) {
        PluginModule module = module(code);
        if (pluginRuntimeGateway.enabled(code)) {
            pluginRuntimeGateway.disable(code);
        }
        if (pluginRuntimeGateway.loaded(code)) {
            pluginRuntimeGateway.unload(code);
        }
        module.markUnloaded();
        pluginModuleRepo.save(module);
        String menuFailure = reconcileUnavailableMenus(code);
        if (menuFailure != null) {
            throw new BizException(menuFailure);
        }
        deleteJar(module);
        pluginModuleRepo.deleteByCode(module.getCode());
    }

    @Transactional(readOnly = true)
    public PluginFrontendManifestDTO frontendManifest() {
        return frontendManifest(false);
    }

    @Transactional(readOnly = true)
    public PluginFrontendManifestDTO frontendManifest(boolean onlyPublicRoutes) {
        Map<String, PluginModule> modules = pluginModuleRepo.findAll().stream()
                .collect(Collectors.toMap(PluginModule::getCode, Function.identity(), (left, right) -> left));
        List<PluginFrontendModuleInfo> runtimeModules = pluginRuntimeGateway.frontendModules().stream()
                .filter(module -> healthy(modules.get(module.pluginCode()), module.pluginCode()))
                .map(module -> onlyPublicRoutes ? PluginFrontendModuleInfo.onlyPublicRoutes(module) : module)
                .toList();
        return PluginAssembler.toManifestDTO(runtimeModules);
    }

    @Transactional(readOnly = true)
    public List<PluginHttpEndpointDTO> httpEndpoints() {
        return pluginRuntimeGateway.httpEndpoints().stream()
                .map(PluginAssembler::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean enabled(String code) {
        return StringUtils.hasText(code) && pluginRuntimeGateway.enabled(code.trim());
    }

    @Transactional(readOnly = true)
    public boolean loaded(String code) {
        return StringUtils.hasText(code) && pluginRuntimeGateway.loaded(code.trim());
    }

    @Transactional(readOnly = true)
    public PluginFrontendAssetDTO frontendAsset(String code, String assetPath) {
        return pluginRuntimeGateway.frontendAsset(code, assetPath)
                .map(PluginAssembler::toDTO)
                .orElseThrow(() -> new BizException("插件前端资源不存在：" + assetPath));
    }

    @Transactional(readOnly = true)
    public PluginHttpDispatchDTO dispatch(PluginHttpDispatchCmd cmd) {
        if (!pluginRuntimeGateway.enabled(cmd.getPluginCode())) {
            throw new BizException("插件未启用");
        }
        return PluginAssembler.toDTO(pluginRuntimeGateway.dispatch(PluginAssembler.toRequest(cmd)));
    }

    @Transactional
    public void restoreEnabledPlugins() {
        syncPluginRegistry();
        Map<String, PluginModule> modules = modulesByCode();
        Set<String> restored = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (PluginModule module : modules.values().stream().sorted(Comparator.comparing(PluginModule::getCode)).toList()) {
            if (!restoreCandidate(module)) {
                disableZombieRuntime(module.getCode());
                String menuFailure = reconcileUnavailableMenus(module.getCode());
                if (menuFailure != null) {
                    markError(module, menuFailure);
                }
                continue;
            }
            restoreEnabledModule(module, modules, restored, visiting);
        }
    }

    private void restoreEnabledModule(PluginModule module, Map<String, PluginModule> modules, Set<String> restored, Set<String> visiting) {
        String code = module.getCode();
        if (restored.contains(code)) {
            restored.add(code);
            return;
        }
        if (!restoreCandidate(module)) {
            return;
        }
        try {
            PluginModule restoredModule;
            if (pluginRuntimeGateway.enabled(code)) {
                restoredModule = projectRuntimeMenus(module);
            } else {
                restoredModule = enableRuntimeWithDependencies(module, modules, restored, visiting);
            }
            restoredModule.setRestoreIntentActive(false);
            pluginModuleRepo.save(restoredModule);
            modules.put(code, restoredModule);
            restored.add(code);
        } catch (Exception e) {
            log.warn("Failed to restore plugin {}", code, e);
            String failure = rootMessage(e);
            String cleanupFailure = cleanupFailedEnable(code);
            markError(module, appendCleanupFailure(failure, cleanupFailure));
        }
    }

    private PluginModule enableRuntimeWithDependencies(PluginModule module, Map<String, PluginModule> modules, Set<String> enabled, Set<String> visiting) {
        String code = module.getCode();
        if (enabled.contains(code)) {
            enabled.add(code);
            return module;
        }
        if (pluginRuntimeGateway.enabled(code)) {
            PluginModule repaired = enableOwnRuntime(module);
            enabled.add(code);
            modules.put(code, repaired);
            return repaired;
        }
        if (!visiting.add(code)) {
            throw new BizException("插件依赖存在循环：" + code);
        }
        try {
            if (!jarExists(module)) {
                throw new BizException("插件 JAR 不存在：" + module.getJarPath());
            }
            enableDependencies(module, modules, enabled, visiting);
            enableAvailableSoftDependencies(module, modules, enabled, visiting);
            PluginModule saved = enableOwnRuntime(module);
            enabled.add(code);
            modules.put(code, saved);
            return saved;
        } finally {
            visiting.remove(code);
        }
    }

    private void enableDependencies(PluginModule module, Map<String, PluginModule> modules, Set<String> enabled, Set<String> visiting) {
        for (String dependencyCode : dependencies(module)) {
            PluginModule dependency = modules.get(dependencyCode);
            if (dependency == null) {
                throw new BizException("插件依赖不存在：" + dependencyCode);
            }
            if (!dependency.enabled() && !Boolean.TRUE.equals(dependency.getRestoreIntentActive())) {
                throw new BizException("请先启用插件依赖：" + dependency.getName());
            }
            if (!enabled.contains(dependencyCode)) {
                enableRuntimeWithDependencies(dependency, modules, enabled, visiting);
            }
            if (!pluginRuntimeGateway.enabled(dependencyCode)) {
                throw new BizException("插件依赖未启用：" + dependency.getName());
            }
        }
    }

    private void migrateRenamedSkinPlugin() {
        pluginModuleRepo.findByCode(LEGACY_SKIN_PLUGIN_CODE).ifPresent(legacy -> {
            PluginModule current = pluginModuleRepo.findByCode(YUDREAM_SKIN_PLUGIN_CODE).orElse(null);
            if (current == null) {
                markError(legacy, "插件已更名为 yudream-skin，请刷新插件目录或构建新插件 JAR");
                return;
            }
            if (legacy.enabled() && !current.enabled()) {
                current.markEnabled();
                pluginModuleRepo.save(current);
            }
            if (pluginRuntimeGateway.enabled(LEGACY_SKIN_PLUGIN_CODE)) {
                pluginRuntimeGateway.disable(LEGACY_SKIN_PLUGIN_CODE);
            }
            if (pluginRuntimeGateway.loaded(LEGACY_SKIN_PLUGIN_CODE)) {
                pluginRuntimeGateway.unload(LEGACY_SKIN_PLUGIN_CODE);
            }
            reconcileUnavailableMenus(LEGACY_SKIN_PLUGIN_CODE);
            pluginModuleRepo.deleteByCode(LEGACY_SKIN_PLUGIN_CODE);
            log.info("Migrated plugin record from {} to {}", LEGACY_SKIN_PLUGIN_CODE, YUDREAM_SKIN_PLUGIN_CODE);
        });
    }

    private void syncDiscoveredPlugins() {
        for (PluginDescriptorInfo descriptor : pluginRuntimeGateway.discover()) {
            PluginModule module = pluginModuleRepo.findByCode(descriptor.code())
                    .orElseGet(() -> PluginModule.fromDescriptor(descriptor));
            module.refreshDescriptor(descriptor);
            pluginModuleRepo.save(module);
        }
    }

    private List<PluginModule> affectedModules(PluginModule target, Map<String, PluginModule> modules) {
        Set<String> affected = new HashSet<>();
        affected.add(target.getCode());
        boolean changed;
        do {
            changed = false;
            for (PluginModule module : modules.values()) {
                if ((!affected.contains(module.getCode()) && dependencies(module).stream().anyMatch(affected::contains))
                        || (!affected.contains(module.getCode()) && softDependencies(module).stream().anyMatch(affected::contains))) {
                    changed |= affected.add(module.getCode());
                }
            }
        } while (changed);
        return affected.stream().map(modules::get)
                .sorted(Comparator.<PluginModule>comparingInt(module -> reverseDependencyDepth(module, modules, affected))
                        .thenComparing(PluginModule::getCode))
                .toList();
    }

    private int reverseDependencyDepth(PluginModule module, Map<String, PluginModule> modules, Set<String> affected) {
        int depth = 0;
        for (PluginModule dependent : modules.values()) {
            if (affected.contains(dependent.getCode()) && dependsOn(dependent, module.getCode())) {
                depth = Math.max(depth, 1 + reverseDependencyDepth(dependent, modules, affected));
            }
        }
        return depth;
    }

    private void stopExistingPlugin(PluginModule existing) {
        if (existing == null) {
            return;
        }
        String code = existing.getCode();
        if (pluginRuntimeGateway.enabled(code)) {
            pluginRuntimeGateway.disable(code);
        }
        if (pluginRuntimeGateway.loaded(code)) {
            pluginRuntimeGateway.unload(code);
        }
        existing.markUnloaded();
        existing.setRestoreIntentActive(false);
        pluginModuleRepo.save(existing);
        reconcileUnavailableMenus(code);
    }

    private void stopAffectedForMarketplaceUpdate(List<PluginModule> affected) {
        for (PluginModule module : affected) {
            String code = module.getCode();
            boolean enabled = pluginRuntimeGateway.enabled(code);
            if (enabled || module.enabled()) {
                module.setRestoreIntentActive(true);
            }
            if (enabled) {
                pluginRuntimeGateway.disable(code);
            }
            if (pluginRuntimeGateway.loaded(code)) {
                pluginRuntimeGateway.unload(code);
            }
            module.markUnloaded();
            pluginModuleRepo.save(module);
            String menuFailure = reconcileUnavailableMenus(code);
            if (menuFailure != null) {
                throw new BizException(menuFailure);
            }
        }
    }

    private boolean restoreMarketplaceBackup(Path originalBackup, Path actualBackup, Path backupOriginal, boolean backupExisted) {
        try {
            if (backupExisted) {
                if (backupOriginal == null || !Files.isRegularFile(backupOriginal)) {
                    return false;
                }
                Files.copy(backupOriginal, originalBackup, StandardCopyOption.REPLACE_EXISTING);
            } else if (actualBackup != null) {
                Files.deleteIfExists(actualBackup);
            }
            return true;
        } catch (IOException restoreFailure) {
            log.error("Failed to restore marketplace backup state", restoreFailure);
            return false;
        }
    }

    private void restoreMarketplaceMetadata(List<PluginModule> affected, Map<String, PluginModuleSnapshot> snapshots) {
        for (PluginModule module : affected) {
            restoreSnapshot(module, snapshots.get(module.getCode()));
            try {
                pluginModuleRepo.save(module);
            } catch (Exception metadataFailure) {
                log.error("Failed to restore plugin metadata for {}", module.getCode(), metadataFailure);
            }
        }
    }

    private Path backupActiveJar(PluginModule module, Path active, Path backup) throws IOException {
        if (!Files.isRegularFile(active)) {
            throw new BizException("当前插件 JAR 不存在：" + active);
        }
        Files.createDirectories(backup.getParent());
        String activeHash = sha256(active);
        if (Files.isRegularFile(backup) && activeHash.equalsIgnoreCase(sha256(backup))) {
            snapshotBackup(module, backup);
            pluginModuleRepo.save(module);
            return backup;
        }
        if (Files.isRegularFile(backup)) {
            backup = backup.getParent().resolve(module.getCode() + "-" +
                    (StringUtils.hasText(module.getPluginVersion()) ? module.getPluginVersion() : "unknown") +
                    "-" + activeHash + ".jar").toAbsolutePath().normalize();
        }
        Path staged = stageJar(active, backup.getParent(), ".plugin-rollback-backup-");
        try {
            moveReplacing(staged, backup);
        } finally {
            deleteQuietly(staged);
        }
        snapshotBackup(module, backup);
        pluginModuleRepo.save(module);
        return backup;
    }

    private void validateBackup(PluginModule module, Path backup, String expectedVersion) {
        if (!backup.startsWith(backupDirectory()) || !Files.isRegularFile(backup)
                || !StringUtils.hasText(module.getBackupSha256())
                || !module.getBackupSha256().equalsIgnoreCase(sha256(backup))) {
            throw new BizException("插件回滚备份无效");
        }
        validateJarAgainstDescriptor(backup, module, expectedVersion, true);
    }

    private void validateJarAgainstDescriptor(Path jar, PluginModule module, String expectedVersion, boolean backup) {
        PluginDescriptorInfo descriptor = pluginRuntimeGateway.describe(jar)
                .orElseThrow(() -> new BizException("插件 JAR 描述信息无效"));
        if (!same(module.getCode(), descriptor.code())
                || (StringUtils.hasText(expectedVersion) && !same(expectedVersion, descriptor.version()))
                || (backup && (!same(module.getBackupName(), descriptor.name())
                || !same(module.getBackupPluginVersion(), descriptor.version())
                || !same(module.getBackupDescription(), descriptor.description())
                || !same(module.getBackupMainClass(), descriptor.mainClass())
                || !same(module.getBackupDependencies(), descriptor.dependencies())
                || !same(module.getBackupSoftDependencies(), descriptor.softDependencies())
                || module.getBackupStatus() == null))) {
            throw new BizException("插件 JAR 描述信息不匹配");
        }
    }

    private PluginModuleSnapshot snapshot(PluginModule module) {
        return new PluginModuleSnapshot(module);
    }

    private void restoreSnapshot(PluginModule module, PluginModuleSnapshot snapshot) {
        snapshot.restore(module);
    }

    private Path stageJar(Path source, Path directory, String prefix) throws IOException {
        Path temp = Files.createTempFile(directory, prefix, ".jar");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (IOException failure) {
            deleteQuietly(temp);
            throw failure;
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void snapshotBackup(PluginModule module, Path backup) {
        module.setBackupJarPath(backup.toString());
        module.setBackupName(module.getName());
        module.setBackupPluginVersion(module.getPluginVersion());
        module.setBackupDescription(module.getDescription());
        module.setBackupMainClass(module.getMainClass());
        module.setBackupSha256(sha256(backup));
        module.setBackupDependencies(copy(module.getDependencies()));
        module.setBackupSoftDependencies(copy(module.getSoftDependencies()));
        module.setBackupStatus(module.getStatus());
        module.setBackupErrorMessage(module.getErrorMessage());
        module.setBackupLoadedAt(module.getLoadedAt());
        module.setBackupEnabledAt(module.getEnabledAt());
        module.setBackupMenusInitialized(module.getMenusInitialized());
        module.setBackupRestoreIntentActive(module.getRestoreIntentActive());
    }

    private void swapBackupMetadata(PluginModule module, Path backup) {
        PluginModule activeSnapshot = PluginModule.builder()
                .name(module.getName()).pluginVersion(module.getPluginVersion()).description(module.getDescription())
                .mainClass(module.getMainClass()).dependencies(copy(module.getDependencies()))
                .softDependencies(copy(module.getSoftDependencies())).status(module.getStatus())
                .errorMessage(module.getErrorMessage()).loadedAt(module.getLoadedAt()).enabledAt(module.getEnabledAt())
                .menusInitialized(module.getMenusInitialized()).restoreIntentActive(module.getRestoreIntentActive()).build();
        restoreBackupSnapshot(module);
        module.setBackupName(activeSnapshot.getName());
        module.setBackupPluginVersion(activeSnapshot.getPluginVersion());
        module.setBackupDescription(activeSnapshot.getDescription());
        module.setBackupMainClass(activeSnapshot.getMainClass());
        module.setBackupSha256(sha256(backup));
        module.setBackupDependencies(activeSnapshot.getDependencies());
        module.setBackupSoftDependencies(activeSnapshot.getSoftDependencies());
        module.setBackupStatus(activeSnapshot.getStatus());
        module.setBackupErrorMessage(activeSnapshot.getErrorMessage());
        module.setBackupLoadedAt(activeSnapshot.getLoadedAt());
        module.setBackupEnabledAt(activeSnapshot.getEnabledAt());
        module.setBackupMenusInitialized(activeSnapshot.getMenusInitialized());
        module.setBackupRestoreIntentActive(activeSnapshot.getRestoreIntentActive());
        module.markUnloaded();
    }

    private void restoreBackupSnapshot(PluginModule module) {
        module.setName(module.getBackupName());
        module.setPluginVersion(module.getBackupPluginVersion());
        module.setDescription(module.getBackupDescription());
        module.setMainClass(module.getBackupMainClass());
        module.setDependencies(copy(module.getBackupDependencies()));
        module.setSoftDependencies(copy(module.getBackupSoftDependencies()));
        module.setStatus(module.getBackupStatus());
        module.setErrorMessage(module.getBackupErrorMessage());
        module.setLoadedAt(module.getBackupLoadedAt());
        module.setEnabledAt(module.getBackupEnabledAt());
        module.setMenusInitialized(module.getBackupMenusInitialized());
        module.setRestoreIntentActive(module.getBackupRestoreIntentActive());
    }

    private void rejectRollbackWhenRunning(PluginModule target) {
        Map<String, PluginModule> modules = modulesByCode();
        Set<String> affected = new HashSet<>();
        affected.add(target.getCode());
        boolean changed;
        do {
            changed = false;
            for (PluginModule module : modules.values()) {
                if (!affected.contains(module.getCode()) && dependencies(module).stream().anyMatch(affected::contains)
                        || !affected.contains(module.getCode()) && softDependencies(module).stream().anyMatch(affected::contains)) {
                    changed |= affected.add(module.getCode());
                }
            }
        } while (changed);
        for (String code : affected) {
            PluginModule module = modules.get(code);
            if (module != null && running(module)) {
                throw new BizException("插件或其依赖方正在运行，需停止后完成回滚");
            }
        }
    }

    private boolean running(PluginModule module) {
        String code = module.getCode();
        return module.getStatus() == online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.LOADED
                || module.getStatus() == online.yudream.base.domain.platform.plugin.enumerate.PluginStatus.ENABLED
                || pluginRuntimeGateway.loaded(code)
                || pluginRuntimeGateway.enabled(code);
    }

    private boolean dependsOn(PluginModule module, String code) {
        return dependencies(module).contains(code) || softDependencies(module).contains(code);
    }

    private List<String> copy(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }

    private boolean same(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    private Path activeJarPath(PluginModule module) {
        if (!StringUtils.hasText(module.getJarPath())) {
            throw new BizException("当前插件 JAR 不存在");
        }
        return Path.of(module.getJarPath()).toAbsolutePath().normalize();
    }

    private Path backupJarPath(PluginModule module) {
        if (!StringUtils.hasText(module.getBackupJarPath())) {
            throw new BizException("插件没有可用回滚备份");
        }
        return Path.of(module.getBackupJarPath()).toAbsolutePath().normalize();
    }

    private Path controlledBackupPath(PluginModule module, Path active) {
        String version = StringUtils.hasText(module.getPluginVersion()) ? module.getPluginVersion() : "unknown";
        return backupDirectory()
                .resolve((module.getCode() + "-" + version + ".jar").replaceAll("[^A-Za-z0-9._-]", "-"))
                .toAbsolutePath().normalize();
    }

    private Path backupDirectory() {
        Path upload = uploadDirectory();
        Path parent = upload.getParent();
        // Keep rollback artifacts outside plugin discovery and upload scanning.
        return (parent == null ? upload.resolveSibling(".plugin-rollback") : parent.resolve(".plugin-rollback"))
                .toAbsolutePath().normalize();
    }

    private String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BizException("插件 JAR 校验失败：" + rootMessage(exception));
        }
    }

    private boolean restoreActiveJar(Path active, Path backup) {
        try {
            if (!Files.isRegularFile(backup)) {
                log.error("Cannot restore plugin JAR: backup is missing at {}", backup);
                return false;
            }
            Files.copy(backup, active, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException restoreFailure) {
            log.error("Failed to restore plugin JAR from {} to {}", backup, active, restoreFailure);
            return false;
        }
    }

    private boolean restoreActiveFromStaged(Path active, Path rollbackTemp) {
        try {
            if (rollbackTemp == null || !Files.isRegularFile(rollbackTemp)) {
                log.error("Cannot restore plugin JAR: rollback staging file is missing at {}", rollbackTemp);
                return false;
            }
            Files.copy(rollbackTemp, active, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException restoreFailure) {
            log.error("Failed to restore plugin JAR from rollback staging file {}", rollbackTemp, restoreFailure);
            return false;
        }
    }

    private boolean restoreBackupFromActive(Path backup, Path rollbackTemp) {
        try {
            if (rollbackTemp == null || !Files.isRegularFile(rollbackTemp)) {
                log.error("Cannot restore rollback backup: staging file is missing at {}", rollbackTemp);
                return false;
            }
            Files.copy(rollbackTemp, backup, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException restoreFailure) {
            log.error("Failed to restore plugin rollback backup from staging file {}", rollbackTemp, restoreFailure);
            return false;
        }
    }

    private List<PluginModuleDTO> modules() {
        return pluginModuleRepo.findAll().stream()
                .sorted(Comparator.comparing(PluginModule::getCode))
                .map(this::toDTO)
                .toList();
    }

    private void deleteJar(PluginModule module) {
        if (!StringUtils.hasText(module.getJarPath())) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(module.getJarPath()));
        } catch (IOException e) {
            throw new BizException("插件文件删除失败：" + e.getMessage());
        }
    }

    private void deleteOldJarIfChanged(PluginModule existing, Path target) throws IOException {
        if (existing == null || !StringUtils.hasText(existing.getJarPath())) {
            return;
        }
        Path oldPath = Path.of(existing.getJarPath()).toAbsolutePath().normalize();
        if (!oldPath.equals(target.toAbsolutePath().normalize())) {
            Files.deleteIfExists(oldPath);
        }
    }

    private Path targetJarPath(Path directory, String filename, PluginModule existing) {
        if (existing != null && StringUtils.hasText(existing.getJarPath())) {
            return Path.of(existing.getJarPath()).toAbsolutePath().normalize();
        }
        return directory.resolve(filename).toAbsolutePath().normalize();
    }

    private Path uploadDirectory() {
        return Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    private void validateJarFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? Path.of(originalFilename).getFileName().toString() : "plugin.jar";
        if (!filename.toLowerCase().endsWith(".jar")) {
            throw new BizException("仅支持上传 .jar 插件文件");
        }
    }

    private String descriptorJarFilename(PluginDescriptorInfo descriptor) {
        String version = StringUtils.hasText(descriptor.version()) ? descriptor.version() : "latest";
        return (descriptor.code() + "-" + version + ".jar").replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private void moveUploadedJar(Path tempFile, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
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

    private void syncPluginRegistry() {
        syncDiscoveredPlugins();
        migrateRenamedSkinPlugin();
    }

    private void syncPluginPermissions(String code) {
        List<PermissionMeta> metas = pluginRuntimeGateway.permissions(code).stream()
                .map(this::toPermissionMeta)
                .toList();
        permissionDomainService.upsertManualPermissions(metas);
        grantPluginPermissionsToSystemRoles(metas);
    }

    /** 将插件权限自动授予系统超管/管理员角色，与菜单权限的自动授权保持一致，避免插件指令因未授权被静默拦截。 */
    private void grantPluginPermissionsToSystemRoles(List<PermissionMeta> metas) {
        if (metas == null || metas.isEmpty()) {
            return;
        }
        for (SystemRoleType roleType : List.of(SystemRoleType.SUPER_ADMIN, SystemRoleType.ADMIN)) {
            Role role = roleRepo.findBySystemType(roleType).orElse(null);
            if (role == null) {
                continue;
            }
            boolean changed = false;
            for (PermissionMeta meta : metas) {
                PermissionID permissionId = PermissionID.of(meta.code());
                if (!role.getPermissions().contains(permissionId)) {
                    role.assignPermission(permissionId);
                    changed = true;
                }
            }
            if (changed) {
                roleRepo.save(role);
            }
        }
    }

    private PluginModule projectRuntimeMenus(PluginModule module) {
        String code = module.getCode();
        List<PluginFrontendModuleInfo> modules = pluginRuntimeGateway.frontendModules().stream()
                .filter(frontendModule -> code.equals(frontendModule.pluginCode()))
                .toList();
        pluginMenuProjectionService.restoreAvailable(code);
        if (module.menusInitialized() && menuSeedSyncMode != SeedSyncMode.MISSING_ONLY) {
            return module;
        }
        pluginMenuProjectionService.project(code, modules);
        if (!module.menusInitialized()) {
            module.markMenusInitialized();
            return pluginModuleRepo.save(module);
        }
        return module;
    }

    private String cleanupFailedEnable(String code) {
        List<String> failures = new ArrayList<>();
        try {
            if (pluginRuntimeGateway.enabled(code)) {
                pluginRuntimeGateway.disable(code);
            }
        } catch (Exception cleanupError) {
            log.warn("Failed to disable plugin runtime after enable failure: {}", code, cleanupError);
            failures.add("运行时禁用失败：" + rootMessage(cleanupError));
        }
        String menuFailure = reconcileUnavailableMenus(code);
        if (menuFailure != null) {
            failures.add(menuFailure);
        }
        return failures.isEmpty() ? null : String.join("；", failures);
    }

    private String reconcileUnavailableMenus(String code) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MENU_CLEANUP_ATTEMPTS; attempt++) {
            try {
                pluginMenuProjectionService.markUnavailable(code);
                return null;
            } catch (RuntimeException cleanupError) {
                lastFailure = cleanupError;
                log.warn("Failed to hide plugin menus for {} on attempt {}/{}: {}",
                        code, attempt, MENU_CLEANUP_ATTEMPTS, rootMessage(cleanupError));
            }
        }
        return "菜单清理失败：" + rootMessage(lastFailure);
    }

    private PluginModule enableOwnRuntime(PluginModule module) {
        String code = module.getCode();
        try {
            if (!pluginRuntimeGateway.loaded(code)) {
                pluginRuntimeGateway.load(module);
                module.markLoaded();
            }
            if (!pluginRuntimeGateway.enabled(code)) {
                pluginRuntimeGateway.enable(module);
            }
            syncPluginPermissions(code);
            module.markEnabled();
            PluginModule saved = pluginModuleRepo.save(module);
            return projectRuntimeMenus(saved);
        } catch (Exception e) {
            String failure = rootMessage(e);
            String cleanupFailure = cleanupFailedEnable(code);
            markError(module, appendCleanupFailure(failure, cleanupFailure));
            throw e;
        }
    }

    private void enableAvailableSoftDependencies(PluginModule module, Map<String, PluginModule> modules,
                                                 Set<String> enabled, Set<String> visiting) {
        for (String dependencyCode : softDependencies(module)) {
            PluginModule dependency = modules.get(dependencyCode);
            if (dependency == null || !dependency.enabled() || enabled.contains(dependencyCode)) {
                continue;
            }
            try {
                enableRuntimeWithDependencies(dependency, modules, enabled, visiting);
            } catch (RuntimeException e) {
                log.warn("Optional plugin dependency {} for {} is unavailable: {}", dependencyCode, module.getCode(), rootMessage(e));
            }
        }
    }

    private void reconcileRuntimeHealth(List<PluginModule> modules) {
        for (PluginModule module : modules) {
            String code = module.getCode();
            if (!healthy(module, code)) {
                disableZombieRuntime(code);
            }
        }
    }

    private boolean healthy(PluginModule module, String code) {
        return module != null && module.enabled() && pluginRuntimeGateway.enabled(code);
    }

    private void disableZombieRuntime(String code) {
        try {
            if (pluginRuntimeGateway.enabled(code)) {
                pluginRuntimeGateway.disable(code);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to disable unhealthy plugin runtime {}: {}", code, rootMessage(e));
        }
    }

    private String appendCleanupFailure(String failure, String cleanupFailure) {
        return cleanupFailure == null ? failure : failure + "；" + cleanupFailure;
    }

    private PermissionMeta toPermissionMeta(PluginPermissionInfo permission) {
        return new PermissionMeta(permission.code(), permission.name(), permission.module(), permission.description());
    }

    private Map<String, PluginModule> modulesByCode() {
        return pluginModuleRepo.findAll().stream()
                .collect(Collectors.toMap(PluginModule::getCode, Function.identity(), (left, right) -> left));
    }

    private List<String> dependencies(PluginModule module) {
        return module.getDependencies() == null ? List.of() : module.getDependencies();
    }

    private List<String> softDependencies(PluginModule module) {
        return module.getSoftDependencies() == null ? List.of() : module.getSoftDependencies();
    }

    private PluginModule module(String code) {
        return pluginModuleRepo.findByCode(code)
                .orElseThrow(() -> new BizException("插件不存在，请先刷新插件目录"));
    }

    private PluginModuleDTO toDTO(PluginModule module) {
        return PluginAssembler.toDTO(
                module,
                pluginRuntimeGateway.loaded(module.getCode()),
                pluginRuntimeGateway.enabled(module.getCode())
        );
    }

    private void markError(PluginModule module, Exception e) {
        markError(module, rootMessage(e));
    }

    private void markError(PluginModule module, String message) {
        if (module == null || !StringUtils.hasText(module.getCode())) {
            return;
        }
        // Runtime activation may have persisted menu/permission state before failing.
        // Reload so the failure state never overwrites a stale optimistic-lock version.
        PluginModule current = pluginModuleRepo.findByCode(module.getCode()).orElse(module);
        current.markError(message);
        pluginModuleRepo.save(current);
    }

    private boolean restoreCandidate(PluginModule module) {
        return Boolean.TRUE.equals(module.getRestoreIntentActive()) || module.enabled();
    }

    private boolean jarExists(PluginModule module) {
        try {
            return module.getJarPath() != null && Files.isRegularFile(Path.of(module.getJarPath()));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private static final class PluginModuleSnapshot {
        private final PluginModule value;

        private PluginModuleSnapshot(PluginModule module) {
            this.value = PluginModule.builder()
                    .code(module.getCode()).name(module.getName()).pluginVersion(module.getPluginVersion())
                    .description(module.getDescription()).mainClass(module.getMainClass()).jarPath(module.getJarPath())
                    .backupJarPath(module.getBackupJarPath()).backupName(module.getBackupName())
                    .backupPluginVersion(module.getBackupPluginVersion()).backupDescription(module.getBackupDescription())
                    .backupMainClass(module.getBackupMainClass()).backupSha256(module.getBackupSha256())
                    .backupDependencies(module.getBackupDependencies() == null ? null : List.copyOf(module.getBackupDependencies()))
                    .backupSoftDependencies(module.getBackupSoftDependencies() == null ? null : List.copyOf(module.getBackupSoftDependencies()))
                    .backupStatus(module.getBackupStatus()).backupErrorMessage(module.getBackupErrorMessage())
                    .backupLoadedAt(module.getBackupLoadedAt()).backupEnabledAt(module.getBackupEnabledAt())
                    .backupMenusInitialized(module.getBackupMenusInitialized()).backupRestoreIntentActive(module.getBackupRestoreIntentActive())
                    .restoreIntentActive(module.getRestoreIntentActive())
                    .dependencies(module.getDependencies() == null ? null : List.copyOf(module.getDependencies()))
                    .softDependencies(module.getSoftDependencies() == null ? null : List.copyOf(module.getSoftDependencies()))
                    .status(module.getStatus()).errorMessage(module.getErrorMessage()).loadedAt(module.getLoadedAt())
                    .enabledAt(module.getEnabledAt()).menusInitialized(module.getMenusInitialized())
                    .id(module.getId()).version(module.getVersion()).createTime(module.getCreateTime())
                    .updateTime(module.getUpdateTime()).build();
        }

        private void restore(PluginModule module) {
            PluginModule copy = value;
            module.setName(copy.getName()); module.setPluginVersion(copy.getPluginVersion());
            module.setDescription(copy.getDescription()); module.setMainClass(copy.getMainClass()); module.setJarPath(copy.getJarPath());
            module.setBackupJarPath(copy.getBackupJarPath()); module.setBackupName(copy.getBackupName());
            module.setBackupPluginVersion(copy.getBackupPluginVersion()); module.setBackupDescription(copy.getBackupDescription());
            module.setBackupMainClass(copy.getBackupMainClass()); module.setBackupSha256(copy.getBackupSha256());
            module.setBackupDependencies(copy.getBackupDependencies()); module.setBackupSoftDependencies(copy.getBackupSoftDependencies());
            module.setBackupStatus(copy.getBackupStatus()); module.setBackupErrorMessage(copy.getBackupErrorMessage());
            module.setBackupLoadedAt(copy.getBackupLoadedAt()); module.setBackupEnabledAt(copy.getBackupEnabledAt());
            module.setBackupMenusInitialized(copy.getBackupMenusInitialized()); module.setBackupRestoreIntentActive(copy.getBackupRestoreIntentActive());
            module.setRestoreIntentActive(copy.getRestoreIntentActive()); module.setDependencies(copy.getDependencies());
            module.setSoftDependencies(copy.getSoftDependencies()); module.setStatus(copy.getStatus()); module.setErrorMessage(copy.getErrorMessage());
            module.setLoadedAt(copy.getLoadedAt()); module.setEnabledAt(copy.getEnabledAt()); module.setMenusInitialized(copy.getMenusInitialized());
        }
    }
}
