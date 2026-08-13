package online.yudream.base.interfaces.platform.plugin.assembler;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdatePlanDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdateResultDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginCompatibilityDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDependencyDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDescriptorDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginDetailDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginJarDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginPublisherDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginSourceDTO;
import online.yudream.base.application.platform.plugin.dto.PluginStorePluginVersionDTO;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendManifestRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendRouteRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdateRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdatePlanRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdateResultRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginCompatibilityRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginDependencyRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginDescriptorRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginDetailRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginJarRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginPublisherRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginSourceRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginVersionRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PluginWebAssembler {

    private PluginWebAssembler() {
    }

    public static List<PluginModuleRes> toResList(List<PluginModuleDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toRes).toList();
    }

    public static PluginModuleRes toRes(PluginModuleDTO dto) {
        return PluginModuleRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .version(dto.getVersion())
                .description(dto.getDescription())
                .mainClass(dto.getMainClass())
                .jarPath(dto.getJarPath())
                .dependencies(dto.getDependencies())
                .softDependencies(dto.getSoftDependencies())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .loadedAt(dto.getLoadedAt())
                .enabledAt(dto.getEnabledAt())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
                .rollbackAvailable(dto.isRollbackAvailable())
                .rollbackVersion(dto.getRollbackVersion())
                .build();
    }

    public static List<PluginMarketplaceUpdateRes> toUpdateResList(List<PluginMarketplaceUpdateDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toUpdateRes).toList();
    }

    public static PluginMarketplaceUpdateRes toUpdateRes(PluginMarketplaceUpdateDTO dto) {
        return PluginMarketplaceUpdateRes.builder()
                .code(dto.getCode())
                .currentVersion(dto.getCurrentVersion())
                .latestVersion(dto.getLatestVersion())
                .latestReleaseVersion(dto.getLatestReleaseVersion())
                .latestDisplayName(dto.getLatestDisplayName())
                .updateAvailable(dto.isUpdateAvailable())
                .compatible(dto.isCompatible())
                .blockedReason(dto.getBlockedReason())
                .build();
    }

    public static PluginMarketplaceUpdateResultRes toUpdateResultRes(PluginMarketplaceUpdateResultDTO dto) {
        return PluginMarketplaceUpdateResultRes.builder()
                .modules(toResList(dto.getModules()))
                .requiresRestart(dto.isRequiresRestart())
                .build();
    }

    public static List<PluginMarketplaceUpdatePlanRes> toUpdatePlanResList(List<PluginMarketplaceUpdatePlanDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toUpdatePlanRes).toList();
    }

    public static PluginMarketplaceUpdatePlanRes toUpdatePlanRes(PluginMarketplaceUpdatePlanDTO dto) {
        return PluginMarketplaceUpdatePlanRes.builder()
                .code(dto.getCode())
                .fromVersion(dto.getFromVersion())
                .toVersion(dto.getToVersion())
                .changeType(dto.getChangeType())
                .requiredDependencies(dto.getRequiredDependencies().stream()
                        .map(PluginWebAssembler::toStoreDependencyRes).toList())
                .optionalDependencies(dto.getOptionalDependencies().stream()
                        .map(PluginWebAssembler::toStoreDependencyRes).toList())
                .affectedEnabledPlugins(dto.getAffectedEnabledPlugins())
                .requiresRestart(dto.isRequiresRestart())
                .blockedReason(dto.getBlockedReason())
                .warnings(dto.getWarnings())
                .build();
    }

    public static List<PluginStorePluginRes> toStoreResList(List<PluginStorePluginDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toStoreRes).toList();
    }

    public static PluginStorePluginRes toStoreRes(PluginStorePluginDTO dto) {
        return PluginStorePluginRes.builder()
                .code(dto.getCode())
                .descriptor(toStoreDescriptorRes(dto.getDescriptor()))
                .build();
    }

    public static PluginStorePluginDetailRes toStoreDetailRes(PluginStorePluginDetailDTO dto) {
        return PluginStorePluginDetailRes.builder()
                .code(dto.getCode())
                .versions(dto.getVersions().stream().map(PluginWebAssembler::toStoreVersionRes).toList())
                .build();
    }

    private static PluginStorePluginVersionRes toStoreVersionRes(PluginStorePluginVersionDTO dto) {
        return PluginStorePluginVersionRes.builder()
                .releaseVersion(dto.getReleaseVersion())
                .descriptor(toStoreDescriptorRes(dto.getDescriptor()))
                .installable(dto.isInstallable())
                .installDisabledReason(dto.getInstallDisabledReason())
                .build();
    }

    private static PluginStorePluginDescriptorRes toStoreDescriptorRes(PluginStorePluginDescriptorDTO dto) {
        return PluginStorePluginDescriptorRes.builder()
                .releaseVersion(dto.getReleaseVersion())
                .code(dto.getCode())
                .version(dto.getVersion())
                .main(dto.getMain())
                .displayName(dto.getDisplayName())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .screenshots(dto.getScreenshots())
                .publisher(toStorePublisherRes(dto.getPublisher()))
                .source(toStoreSourceRes(dto.getSource()))
                .license(dto.getLicense())
                .releaseNotes(dto.getReleaseNotes())
                .compatibility(toStoreCompatibilityRes(dto.getCompatibility()))
                .dependencies(dto.getDependencies() == null ? List.of() : dto.getDependencies().stream()
                        .map(PluginWebAssembler::toStoreDependencyRes).toList())
                .jar(toStoreJarRes(dto.getJar()))
                .build();
    }

    private static PluginStorePluginPublisherRes toStorePublisherRes(PluginStorePluginPublisherDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginStorePluginPublisherRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .url(dto.getUrl())
                .verified(dto.isVerified())
                .build();
    }

    private static PluginStorePluginSourceRes toStoreSourceRes(PluginStorePluginSourceDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginStorePluginSourceRes.builder()
                .repository(dto.getRepository())
                .commit(dto.getCommit())
                .build();
    }

    private static PluginStorePluginCompatibilityRes toStoreCompatibilityRes(PluginStorePluginCompatibilityDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginStorePluginCompatibilityRes.builder()
                .host(dto.getHost())
                .spi(dto.getSpi())
                .frontendSdk(dto.getFrontendSdk())
                .build();
    }

    private static PluginStorePluginDependencyRes toStoreDependencyRes(PluginStorePluginDependencyDTO dto) {
        return PluginStorePluginDependencyRes.builder()
                .code(dto.getCode())
                .range(dto.getRange())
                .required(dto.isRequired())
                .warning(dto.isWarning())
                .warningReason(dto.getWarningReason())
                .build();
    }

    private static PluginStorePluginJarRes toStoreJarRes(PluginStorePluginJarDTO dto) {
        return PluginStorePluginJarRes.builder()
                .mavenCoordinates(dto.getMavenCoordinates())
                .url(dto.getUrl())
                .sha256(dto.getSha256())
                .build();
    }

    public static PluginFrontendManifestRes toRes(PluginFrontendManifestDTO dto) {
        return PluginFrontendManifestRes.builder()
                .sdkVersion(dto.getSdkVersion())
                .modules(dto.getModules().stream().map(PluginWebAssembler::toRes).toList())
                .build();
    }

    public static PluginFrontendModuleRes toRes(PluginFrontendModuleDTO dto) {
        return PluginFrontendModuleRes.builder()
                .pluginCode(dto.getPluginCode())
                .entry(dto.getEntry())
                .moduleName(dto.getModuleName())
                .sdkVersion(dto.getSdkVersion())
                .integrity(dto.getIntegrity())
                .menuTitle(dto.getMenuTitle())
                .menuIcon(dto.getMenuIcon())
                .menuSort(dto.getMenuSort())
                .parentCode(dto.getParentCode())
                .visible(dto.getVisible())
                .status(dto.getStatus())
                .menuCode(dto.getMenuCode())
                .menuType(dto.getMenuType())
                .menuModule(dto.getMenuModule())
                .menuPath(dto.getMenuPath())
                .menuComponent(dto.getMenuComponent())
                .menuLink(dto.getMenuLink())
                .menuPermission(dto.getMenuPermission())
                .styles(dto.getStyles())
                .scripts(dto.getScripts())
                .routes(dto.getRoutes().stream().map(PluginWebAssembler::toRes).toList())
                .build();
    }

    public static PluginFrontendRouteRes toRes(PluginFrontendRouteDTO dto) {
        return PluginFrontendRouteRes.builder()
                .path(dto.getPath())
                .name(dto.getName())
                .title(dto.getTitle())
                .icon(dto.getIcon())
                .parentPath(dto.getParentPath())
                .parentTitle(dto.getParentTitle())
                .parentIcon(dto.getParentIcon())
                .parentSort(dto.getParentSort())
                .component(dto.getComponent())
                .permission(dto.getPermission())
                .sort(dto.getSort())
                .hideInMenu(dto.getHideInMenu())
                .parentCode(dto.getParentCode())
                .visible(dto.getVisible())
                .status(dto.getStatus())
                .menuCode(dto.getMenuCode())
                .type(dto.getType())
                .module(dto.getModule())
                .link(dto.getLink())
                .parentMenuCode(dto.getParentMenuCode())
                .parentParentCode(dto.getParentParentCode())
                .parentType(dto.getParentType())
                .parentModule(dto.getParentModule())
                .parentComponent(dto.getParentComponent())
                .parentLink(dto.getParentLink())
                .parentPermission(dto.getParentPermission())
                .parentVisible(dto.getParentVisible())
                .parentStatus(dto.getParentStatus())
                .publicAccess(dto.getPublicAccess())
                .build();
    }

    public static PluginHttpDispatchCmd toDispatchCmd(
            String pluginCode,
            String pluginPath,
            String body,
            HttpServletRequest request,
            SecurityPrincipalSupport.SecurityPrincipal principal
    ) {
        PluginHttpDispatchCmd cmd = new PluginHttpDispatchCmd();
        cmd.setPluginCode(pluginCode);
        cmd.setMethod(request.getMethod());
        cmd.setPath(pluginPath);
        cmd.setBody(body);
        cmd.setHeaders(headers(request));
        cmd.setQuery(query(request));
        cmd.setUserId(principal.userId());
        cmd.setPermissions(principal.permissions());
        return cmd;
    }

    public static String frontendAssetPath(String pluginCode, HttpServletRequest request) {
        String prefix = "/api/platform/plugins/" + pluginCode + "/assets";
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix)) {
            return "";
        }
        String path = uri.substring(prefix.length());
        return path.isBlank() ? "" : path;
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .collect(Collectors.toMap(name -> name, name -> Collections.list(request.getHeaders(name)), (a, b) -> a));
    }

    private static Map<String, List<String>> query(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue()), (a, b) -> a));
    }
}
