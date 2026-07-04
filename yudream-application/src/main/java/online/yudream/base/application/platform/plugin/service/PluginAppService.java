package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.system.security.PermissionMeta;
import online.yudream.base.domain.system.user.service.PermissionDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAppService {

    private final PluginModuleRepo pluginModuleRepo;
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final PermissionDomainService permissionDomainService;

    @Transactional
    public List<PluginModuleDTO> list() {
        syncDiscoveredPlugins();
        return pluginModuleRepo.findAll().stream()
                .sorted(Comparator.comparing(PluginModule::getCode))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<PluginModuleDTO> refresh() {
        syncDiscoveredPlugins();
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
        PluginModule module = module(code);
        assertDependenciesEnabled(module);
        try {
            if (!pluginRuntimeGateway.loaded(code)) {
                pluginRuntimeGateway.load(module);
                module.markLoaded();
            }
            pluginRuntimeGateway.enable(module);
            syncPluginPermissions(code);
            module.markEnabled();
            return toDTO(pluginModuleRepo.save(module));
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

    @Transactional(readOnly = true)
    public PluginFrontendManifestDTO frontendManifest() {
        return PluginAssembler.toManifestDTO(pluginRuntimeGateway.frontendModules());
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
        for (PluginModule module : pluginModuleRepo.findAll()) {
            if (!module.enabled()) {
                continue;
            }
            try {
                assertDependenciesEnabled(module);
                pluginRuntimeGateway.load(module);
                pluginRuntimeGateway.enable(module);
                syncPluginPermissions(module.getCode());
                module.markEnabled();
                pluginModuleRepo.save(module);
            } catch (Exception e) {
                log.warn("Failed to restore plugin {}", module.getCode(), e);
                markError(module, e);
            }
        }
    }

    private void syncDiscoveredPlugins() {
        for (PluginDescriptorInfo descriptor : pluginRuntimeGateway.discover()) {
            PluginModule module = pluginModuleRepo.findByCode(descriptor.code())
                    .orElseGet(() -> PluginModule.fromDescriptor(descriptor));
            module.refreshDescriptor(descriptor);
            pluginModuleRepo.save(module);
        }
    }

    private void syncPluginPermissions(String code) {
        List<PermissionMeta> metas = pluginRuntimeGateway.permissions(code).stream()
                .map(this::toPermissionMeta)
                .toList();
        permissionDomainService.upsertManualPermissions(metas);
    }

    private PermissionMeta toPermissionMeta(PluginPermissionInfo permission) {
        return new PermissionMeta(permission.code(), permission.name(), permission.module(), permission.description());
    }

    private void assertDependenciesEnabled(PluginModule module) {
        List<String> dependencies = module.getDependencies() == null ? List.of() : module.getDependencies();
        for (String dependencyCode : dependencies) {
            PluginModule dependency = pluginModuleRepo.findByCode(dependencyCode)
                    .orElseThrow(() -> new BizException("插件依赖不存在：" + dependencyCode));
            if (!pluginRuntimeGateway.enabled(dependencyCode) && !dependency.enabled()) {
                throw new BizException("请先启用插件依赖：" + dependency.getName());
            }
        }
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
        module.markError(rootMessage(e));
        pluginModuleRepo.save(module);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}
