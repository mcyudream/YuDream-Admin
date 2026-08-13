package online.yudream.base.application.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendAssetDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.dto.PluginHttpEndpointDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginCompatibilityDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDependencyDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDescriptorDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDetailDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginJarDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginPublisherDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginSourceDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginVersionDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginCompatibility;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginPublisher;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginSource;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginVersion;

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
                .softDependencies(module.getSoftDependencies())
                .status(module.getStatus())
                .errorMessage(module.getErrorMessage())
                .loadedAt(module.getLoadedAt())
                .enabledAt(module.getEnabledAt())
                .loaded(loaded)
                .enabled(enabled)
                .rollbackAvailable(module.getBackupJarPath() != null)
                .rollbackVersion(module.getBackupPluginVersion())
                .build();
    }

    public static PluginMarketplaceUpdateDTO toDTO(PluginMarketplaceUpdateDTO update) {
        return PluginMarketplaceUpdateDTO.builder()
                .code(update.getCode())
                .currentVersion(update.getCurrentVersion())
                .latestVersion(update.getLatestVersion())
                .latestReleaseVersion(update.getLatestReleaseVersion())
                .latestDisplayName(update.getLatestDisplayName())
                .updateAvailable(update.isUpdateAvailable())
                .compatible(update.isCompatible())
                .blockedReason(update.getBlockedReason())
                .build();
    }

    public static PluginStorePluginDTO toDTO(PluginStorePluginInfo plugin) {
        return PluginStorePluginDTO.builder()
                .code(plugin.getCode())
                .descriptor(toDTO(plugin.getDescriptor()))
                .build();
    }

    public static PluginStorePluginDetailDTO toDTO(PluginStorePluginDetail detail) {
        return PluginStorePluginDetailDTO.builder()
                .code(detail.code())
                .versions(detail.versions().stream().map(PluginAssembler::toDTO).toList())
                .build();
    }

    public static PluginStorePluginVersionDTO toDTO(PluginStorePluginVersion version) {
        return PluginStorePluginVersionDTO.builder()
                .releaseVersion(version.releaseVersion())
                .descriptor(toDTO(version.descriptor()))
                .build();
    }

    private static PluginStorePluginDescriptorDTO toDTO(PluginStorePluginDescriptor descriptor) {
        return PluginStorePluginDescriptorDTO.builder()
                .releaseVersion(descriptor.releaseVersion())
                .code(descriptor.code())
                .version(descriptor.version())
                .main(descriptor.main())
                .displayName(descriptor.displayName())
                .description(descriptor.description())
                .icon(descriptor.icon())
                .screenshots(descriptor.screenshots())
                .publisher(toDTO(descriptor.publisher()))
                .source(toDTO(descriptor.source()))
                .license(descriptor.license())
                .releaseNotes(descriptor.releaseNotes())
                .compatibility(toDTO(descriptor.compatibility()))
                .dependencies(descriptor.dependencies().stream().map(PluginAssembler::toDTO).toList())
                .jar(toDTO(descriptor.jar()))
                .build();
    }

    private static PluginStorePluginPublisherDTO toDTO(PluginStorePluginPublisher publisher) {
        if (publisher == null) {
            return null;
        }
        return PluginStorePluginPublisherDTO.builder()
                .id(publisher.id())
                .name(publisher.name())
                .url(publisher.url())
                .verified(publisher.verified())
                .build();
    }

    private static PluginStorePluginSourceDTO toDTO(PluginStorePluginSource source) {
        if (source == null) {
            return null;
        }
        return PluginStorePluginSourceDTO.builder()
                .repository(source.repository())
                .commit(source.commit())
                .build();
    }

    private static PluginStorePluginCompatibilityDTO toDTO(PluginStorePluginCompatibility compatibility) {
        if (compatibility == null) {
            return null;
        }
        return PluginStorePluginCompatibilityDTO.builder()
                .host(compatibility.host())
                .spi(compatibility.spi())
                .frontendSdk(compatibility.frontendSdk())
                .build();
    }

    public static PluginStorePluginDependencyDTO toDTO(PluginStorePluginDependency dependency) {
        return PluginStorePluginDependencyDTO.builder()
                .code(dependency.code())
                .range(dependency.range())
                .required(dependency.required())
                .build();
    }

    private static PluginStorePluginJarDTO toDTO(PluginStorePluginJar jar) {
        return PluginStorePluginJarDTO.builder()
                .mavenCoordinates(jar.mavenCoordinates())
                .url(jar.url())
                .sha256(jar.sha256())
                .build();
    }

    public static PluginFrontendManifestDTO toManifestDTO(List<PluginFrontendModuleInfo> modules) {
        return PluginFrontendManifestDTO.builder()
                .sdkVersion("1.0.0")
                .modules(modules == null ? List.of() : modules.stream().map(PluginAssembler::toRuntimeDTO).toList())
                .build();
    }

    private static PluginFrontendModuleDTO toRuntimeDTO(PluginFrontendModuleInfo module) {
        return PluginFrontendModuleDTO.builder()
                .pluginCode(module.pluginCode())
                .entry(module.entry())
                .moduleName(module.moduleName())
                .sdkVersion(module.sdkVersion())
                .integrity(module.integrity())
                .menuTitle(module.menuTitle())
                .menuIcon(module.menuIcon())
                .menuSort(module.menuSort())
                .styles(module.styles())
                .scripts(module.scripts())
                .routes(module.routes() == null ? List.of() : module.routes().stream().map(PluginAssembler::toDTO).toList())
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
                .styles(module.styles())
                .scripts(module.scripts())
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
                .hideInMenu(route.hideInMenu())
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
                .publicAccess(route.publicAccess())
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
