package online.yudream.base.application.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendAssetDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpEndpointDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;

import java.util.List;

public class PluginAssembler {

    private PluginAssembler() {
    }

    public static PluginModuleDTO toDTO(PluginModule module, boolean loaded, boolean enabled) {
        return PluginModuleDTO.builder()
                .id(module.getId())
                .code(module.getCode())
                .name(module.getName())
                .version(module.getPluginVersion())
                .description(module.getDescription())
                .mainClass(module.getMainClass())
                .jarPath(module.getJarPath())
                .dependencies(module.getDependencies())
                .status(module.getStatus())
                .errorMessage(module.getErrorMessage())
                .loadedAt(module.getLoadedAt())
                .enabledAt(module.getEnabledAt())
                .loaded(loaded)
                .enabled(enabled)
                .build();
    }

    public static PluginFrontendManifestDTO toManifestDTO(List<PluginFrontendModuleInfo> modules) {
        return PluginFrontendManifestDTO.builder()
                .sdkVersion("1.0.0")
                .modules(modules == null ? List.of() : modules.stream().map(PluginAssembler::toDTO).toList())
                .build();
    }

    public static PluginFrontendModuleDTO toDTO(PluginFrontendModuleInfo module) {
        return PluginFrontendModuleDTO.builder()
                .pluginCode(module.pluginCode())
                .entry(module.entry())
                .moduleName(module.moduleName())
                .sdkVersion(module.sdkVersion())
                .integrity(module.integrity())
                .menuTitle(module.menuTitle())
                .menuIcon(module.menuIcon())
                .menuSort(module.menuSort())
                .parentCode(module.parentCode())
                .visible(module.visible())
                .status(module.status())
                .menuCode(module.menuCode())
                .menuType(module.menuType())
                .menuModule(module.menuModule())
                .menuPath(module.menuPath())
                .menuComponent(module.menuComponent())
                .menuLink(module.menuLink())
                .menuPermission(module.menuPermission())
                .routes(module.routes().stream().map(PluginAssembler::toDTO).toList())
                .build();
    }

    public static PluginFrontendRouteDTO toDTO(PluginFrontendRouteInfo route) {
        return PluginFrontendRouteDTO.builder()
                .path(route.path())
                .name(route.name())
                .title(route.title())
                .icon(route.icon())
                .parentPath(route.parentPath())
                .parentTitle(route.parentTitle())
                .parentIcon(route.parentIcon())
                .parentSort(route.parentSort())
                .component(route.component())
                .permission(route.permission())
                .sort(route.sort())
                .parentCode(route.parentCode())
                .visible(route.visible())
                .status(route.status())
                .menuCode(route.menuCode())
                .type(route.type())
                .module(route.module())
                .link(route.link())
                .parentMenuCode(route.parentMenuCode())
                .parentParentCode(route.parentParentCode())
                .parentType(route.parentType())
                .parentModule(route.parentModule())
                .parentComponent(route.parentComponent())
                .parentLink(route.parentLink())
                .parentPermission(route.parentPermission())
                .parentVisible(route.parentVisible())
                .parentStatus(route.parentStatus())
                .build();
    }

    public static PluginFrontendAssetDTO toDTO(PluginFrontendAssetInfo asset) {
        return PluginFrontendAssetDTO.builder()
                .path(asset.path())
                .contentType(asset.contentType())
                .body(asset.body())
                .build();
    }

    public static PluginHttpDispatchRequest toRequest(PluginHttpDispatchCmd cmd) {
        return new PluginHttpDispatchRequest(
                cmd.getPluginCode(),
                cmd.getMethod(),
                cmd.getPath(),
                cmd.getHeaders(),
                cmd.getQuery(),
                cmd.getBody(),
                cmd.getUserId(),
                cmd.getPermissions()
        );
    }

    public static PluginHttpDispatchDTO toDTO(PluginHttpDispatchResult result) {
        return PluginHttpDispatchDTO.builder()
                .status(result.status())
                .headers(result.headers())
                .contentType(result.contentType())
                .body(result.body())
                .wrapped(result.wrapped())
                .build();
    }

    public static PluginHttpEndpointDTO toDTO(PluginHttpEndpointInfo endpoint) {
        return PluginHttpEndpointDTO.builder()
                .pluginCode(endpoint.pluginCode())
                .method(endpoint.method())
                .path(endpoint.path())
                .fullPath(endpoint.fullPath())
                .permission(endpoint.permission())
                .wrapResult(endpoint.wrapResult())
                .build();
    }
}
