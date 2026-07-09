package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginFrontendRouteSortSaveCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginFrontendSortSaveCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendAssetDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpEndpointDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteSortSetting;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendSortSetting;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.system.security.PermissionMeta;
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

    @Value("${yudream.platform.plugin.upload-directory:plugins}")
    private String uploadDirectory;

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
            PluginDescriptorInfo descriptor = pluginRuntimeGateway.describe(tempFile)
                    .orElseThrow(() -> new BizException("上传文件不是有效的 YuDream 插件 JAR"));
            PluginModule existing = pluginModuleRepo.findByCode(descriptor.code()).orElse(null);
            Path target = targetJarPath(directory, descriptorJarFilename(descriptor), existing);
            replaceExistingPlugin(existing);
            moveUploadedJar(tempFile, target);
            tempFile = null;
            deleteOldJarIfChanged(existing, target);
            syncPluginRegistry();
            return pluginModuleRepo.findAll().stream()
                    .sorted(Comparator.comparing(PluginModule::getCode))
                    .map(this::toDTO)
                    .toList();
        } catch (IOException e) {
            throw new BizException("插件 JAR 上传失败：" + e.getMessage());
        } finally {
            deleteQuietly(tempFile);
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
            return toDTO(enableRuntimeWithDependencies(module, modules, new HashSet<>(), new HashSet<>()));
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
        PluginModule saved = pluginModuleRepo.save(module);
        reconcileUnavailableMenus(code);
        if (runtimeFailure != null) {
            throw new BizException("插件禁用失败：" + rootMessage(runtimeFailure));
        }
        return toDTO(saved);
    }

    @Transactional
    public PluginModuleDTO unload(String code) {
        PluginModule module = module(code);
        pluginRuntimeGateway.unload(code);
        module.markUnloaded();
        PluginModule saved = pluginModuleRepo.save(module);
        reconcileUnavailableMenus(code);
        return toDTO(saved);
    }

    @Transactional
    public void delete(String code) {
        PluginModule module = module(code);
        if (pluginRuntimeGateway.enabled(code)) {
            pluginRuntimeGateway.disable(code);
        }
        if (pluginRuntimeGateway.loaded(code)) {
            pluginRuntimeGateway.unload(code);
        }
        reconcileUnavailableMenus(code);
        deleteJar(module);
        pluginModuleRepo.deleteByCode(module.getCode());
    }

    @Transactional(readOnly = true)
    public PluginFrontendManifestDTO frontendManifest() {
        Map<String, PluginModule> modules = pluginModuleRepo.findAll().stream()
                .collect(Collectors.toMap(PluginModule::getCode, Function.identity(), (left, right) -> left));
        List<PluginFrontendModuleInfo> runtimeModules = pluginRuntimeGateway.frontendModules().stream()
                .filter(module -> healthy(modules.get(module.pluginCode()), module.pluginCode()))
                .map(module -> applyFrontendSortSetting(module, modules.get(module.pluginCode())))
                .toList();
        return PluginAssembler.toManifestDTO(pluginMenuProjectionService.applyOverrides(runtimeModules));
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

    @Transactional
    public PluginFrontendModuleDTO saveFrontendSort(String code, PluginFrontendSortSaveCmd cmd) {
        PluginModule module = module(code);
        PluginFrontendModuleInfo frontendModule = currentFrontendModule(code, cmd.getModuleName());
        PluginFrontendSortSetting setting = toSortSetting(frontendModule, cmd);
        module.saveFrontendSortSetting(setting);
        pluginModuleRepo.save(module);
        return PluginAssembler.toDTO(applyFrontendSortSetting(frontendModule, module));
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
            if (!module.enabled()) {
                disableZombieRuntime(module.getCode());
                reconcileUnavailableMenus(module.getCode());
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
        if (!module.enabled()) {
            return;
        }
        try {
            if (pluginRuntimeGateway.enabled(code)) {
                projectRuntimeMenus(code);
            } else {
                enableRuntimeWithDependencies(module, modules, restored, visiting);
            }
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
            if (!dependency.enabled()) {
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

    private void replaceExistingPlugin(PluginModule existing) {
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
        pluginModuleRepo.save(existing);
        reconcileUnavailableMenus(code);
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
    }

    private void projectRuntimeMenus(String code) {
        List<PluginFrontendModuleInfo> modules = pluginRuntimeGateway.frontendModules().stream()
                .filter(module -> code.equals(module.pluginCode()))
                .toList();
        pluginMenuProjectionService.project(code, modules);
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

    private void reconcileUnavailableMenusExcept(Set<String> runtimeEnabledCodes) {
        for (int attempt = 1; attempt <= MENU_CLEANUP_ATTEMPTS; attempt++) {
            try {
                pluginMenuProjectionService.markUnavailableExcept(runtimeEnabledCodes);
                return;
            } catch (RuntimeException cleanupError) {
                log.warn("Failed to reconcile inactive plugin menus on attempt {}/{}: {}",
                        attempt, MENU_CLEANUP_ATTEMPTS, rootMessage(cleanupError));
            }
        }
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
            projectRuntimeMenus(code);
            return saved;
        } catch (Exception e) {
            String failure = rootMessage(e);
            String cleanupFailure = cleanupFailedEnable(code);
            markError(module, appendCleanupFailure(failure, cleanupFailure));
            throw e;
        }
    }

    private void reconcileRuntimeHealth(List<PluginModule> modules) {
        Set<String> healthyCodes = new HashSet<>();
        for (PluginModule module : modules) {
            String code = module.getCode();
            if (healthy(module, code)) {
                healthyCodes.add(code);
            } else {
                disableZombieRuntime(code);
            }
        }
        reconcileUnavailableMenusExcept(healthyCodes);
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

    private PluginFrontendModuleInfo currentFrontendModule(String code, String moduleName) {
        List<PluginFrontendModuleInfo> modules = pluginRuntimeGateway.frontendModules().stream()
                .filter(module -> module.pluginCode().equals(code))
                .toList();
        if (modules.isEmpty()) {
            throw new BizException("插件未启用，暂无注册菜单路由");
        }
        String target = normalize(moduleName);
        if (!target.isEmpty()) {
            return modules.stream()
                    .filter(module -> normalize(module.moduleName()).equals(target))
                    .findFirst()
                    .orElseThrow(() -> new BizException("插件前端模块不存在：" + moduleName));
        }
        if (modules.size() == 1) {
            return modules.get(0);
        }
        throw new BizException("请指定插件前端模块");
    }

    private PluginFrontendSortSetting toSortSetting(PluginFrontendModuleInfo module, PluginFrontendSortSaveCmd cmd) {
        List<PluginFrontendRouteSortSetting> routes = (cmd.getRoutes() == null ? List.<PluginFrontendRouteSortSaveCmd>of() : cmd.getRoutes()).stream()
                .map(route -> toRouteSortSetting(module, route))
                .toList();
        return new PluginFrontendSortSetting(module.moduleName(), cmd.getMenuSort(), routes);
    }

    private PluginFrontendRouteSortSetting toRouteSortSetting(PluginFrontendModuleInfo module, PluginFrontendRouteSortSaveCmd cmd) {
        PluginFrontendRouteInfo route = module.routes().stream()
                .filter(item -> same(item.path(), cmd.getPath()) || same(item.name(), cmd.getName()))
                .findFirst()
                .orElseThrow(() -> new BizException("插件菜单路由不存在：" + (cmd.getPath() == null ? cmd.getName() : cmd.getPath())));
        return new PluginFrontendRouteSortSetting(route.path(), route.name(), cmd.getSort(), cmd.getParentSort());
    }

    private PluginFrontendModuleInfo applyFrontendSortSetting(PluginFrontendModuleInfo module, PluginModule pluginModule) {
        if (pluginModule == null) {
            return module;
        }
        PluginFrontendSortSetting setting = pluginModule.frontendSortSetting(module.moduleName());
        if (setting == null) {
            return module;
        }
        return module.withSortOverrides(
                setting.menuSort() == null ? module.menuSort() : setting.menuSort(),
                module.routes().stream().map(route -> applyRouteSortSetting(route, setting)).toList()
        );
    }

    private PluginFrontendRouteInfo applyRouteSortSetting(PluginFrontendRouteInfo route, PluginFrontendSortSetting setting) {
        PluginFrontendRouteSortSetting routeSetting = setting.routeSetting(route);
        if (routeSetting == null) {
            return route;
        }
        return route.withSortOverrides(
                routeSetting.parentSort() == null ? route.parentSort() : routeSetting.parentSort(),
                routeSetting.sort() == null ? route.sort() : routeSetting.sort()
        );
    }

    private static boolean same(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
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
        module.markError(message);
        pluginModuleRepo.save(module);
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
}
