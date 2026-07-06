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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
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

    private final PluginModuleRepo pluginModuleRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final PermissionDomainService permissionDomainService;

    @Transactional
    public List<PluginModuleDTO> list() {
        syncPluginRegistry();
        return pluginModuleRepo.findAll().stream()
                .sorted(Comparator.comparing(PluginModule::getCode))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<PluginModuleDTO> refresh() {
        return list();
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

    @Transactional
    public PluginModuleDTO enable(String code) {
        Map<String, PluginModule> modules = modulesByCode();
        PluginModule module = modules.get(code);
        if (module == null) {
            throw new BizException("插件不存在，请先刷新插件目录");
        }
        try {
            return toDTO(enableRuntimeWithDependencies(module, modules, new HashSet<>(), new HashSet<>()));
        } catch (Exception e) {
            markError(module, e);
            throw new BizException("插件启用失败：" + rootMessage(e));
        }
    }

    @Transactional
    public PluginModuleDTO disable(String code) {
        PluginModule module = module(code);
        pluginRuntimeGateway.disable(code);
        module.markDisabled();
        return toDTO(pluginModuleRepo.save(module));
    }

    @Transactional
    public PluginModuleDTO unload(String code) {
        PluginModule module = module(code);
        pluginRuntimeGateway.unload(code);
        module.markUnloaded();
        return toDTO(pluginModuleRepo.save(module));
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
        pluginModuleRepo.deleteByCode(module.getCode());
    }

    @Transactional(readOnly = true)
    public PluginFrontendManifestDTO frontendManifest() {
        Map<String, PluginModule> modules = pluginModuleRepo.findAll().stream()
                .collect(Collectors.toMap(PluginModule::getCode, Function.identity(), (left, right) -> left));
        return PluginAssembler.toManifestDTO(pluginRuntimeGateway.frontendModules().stream()
                .map(module -> applyFrontendSortSetting(module, modules.get(module.pluginCode())))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<PluginHttpEndpointDTO> httpEndpoints() {
        return pluginRuntimeGateway.httpEndpoints().stream()
                .map(PluginAssembler::toDTO)
                .toList();
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
                continue;
            }
            restoreEnabledModule(module, modules, restored, visiting);
        }
    }

    private void restoreEnabledModule(PluginModule module, Map<String, PluginModule> modules, Set<String> restored, Set<String> visiting) {
        String code = module.getCode();
        if (restored.contains(code) || pluginRuntimeGateway.enabled(code)) {
            restored.add(code);
            return;
        }
        if (!module.enabled()) {
            return;
        }
        try {
            enableRuntimeWithDependencies(module, modules, restored, visiting);
            restored.add(code);
        } catch (Exception e) {
            log.warn("Failed to restore plugin {}", code, e);
            markError(module, e);
        }
    }

    private PluginModule enableRuntimeWithDependencies(PluginModule module, Map<String, PluginModule> modules, Set<String> enabled, Set<String> visiting) {
        String code = module.getCode();
        if (enabled.contains(code) || pluginRuntimeGateway.enabled(code)) {
            enabled.add(code);
            return module;
        }
        if (!visiting.add(code)) {
            throw new BizException("插件依赖存在循环：" + code);
        }
        try {
            if (!jarExists(module)) {
                throw new BizException("插件 JAR 不存在：" + module.getJarPath());
            }
            enableDependencies(module, modules, enabled, visiting);
            if (!pluginRuntimeGateway.loaded(code)) {
                pluginRuntimeGateway.load(module);
                module.markLoaded();
            }
            pluginRuntimeGateway.enable(module);
            syncPluginPermissions(code);
            module.markEnabled();
            PluginModule saved = pluginModuleRepo.save(module);
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
            if (!pluginRuntimeGateway.enabled(dependencyCode)) {
                if (!dependency.enabled()) {
                    throw new BizException("请先启用插件依赖：" + dependency.getName());
                }
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
        return new PluginFrontendModuleInfo(
                module.pluginCode(),
                module.entry(),
                module.moduleName(),
                module.sdkVersion(),
                module.integrity(),
                module.menuTitle(),
                module.menuIcon(),
                setting.menuSort() == null ? module.menuSort() : setting.menuSort(),
                module.routes().stream().map(route -> applyRouteSortSetting(route, setting)).toList()
        );
    }

    private PluginFrontendRouteInfo applyRouteSortSetting(PluginFrontendRouteInfo route, PluginFrontendSortSetting setting) {
        PluginFrontendRouteSortSetting routeSetting = setting.routeSetting(route);
        if (routeSetting == null) {
            return route;
        }
        return new PluginFrontendRouteInfo(
                route.path(),
                route.name(),
                route.title(),
                route.icon(),
                route.parentPath(),
                route.parentTitle(),
                route.parentIcon(),
                routeSetting.parentSort() == null ? route.parentSort() : routeSetting.parentSort(),
                route.component(),
                route.permission(),
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
