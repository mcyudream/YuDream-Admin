package online.yudream.base.interfaces.platform.plugin.assembler;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketplaceBatchInstallCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.dto.PluginGlobalWidgetDTO;
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
import online.yudream.base.application.platform.plugin.dto.PluginThemeDTO;
import online.yudream.base.application.platform.plugin.dto.PluginThemeOverviewDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMessagingConnectionDTO;
import online.yudream.base.application.platform.plugin.dto.PluginMessagingGroupDTO;
import online.yudream.base.application.platform.plugin.dto.PluginAiAgentCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginAiProviderCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginDeptCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginRoleCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginUserCatalogDTO;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketplaceBatchInstallRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginPluginActionRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginDependencyStatusItemRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginDependencyStatusRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginDependentRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginDependentsRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendManifestRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendRouteRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginGlobalWidgetRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceInstallPlanEntryRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceInstallPlanRes;
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
import online.yudream.base.interfaces.platform.plugin.res.PluginThemeOverviewRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginThemeRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMessagingConnectionRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMessagingGroupRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginAiAgentCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginAiModelCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginAiProviderCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginDeptCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginRoleCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginUserCatalogRes;
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

    /** 启用请求携带的软依赖集合；空请求返回空集。 */
    public static java.util.Set<String> includeSoftDependencies(PluginPluginActionRequest request) {
        if (request == null || request.getIncludeSoftDependencies() == null) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(request.getIncludeSoftDependencies());
    }

    /** 卸载/删除请求是否级联处理依赖方；空请求不级联。 */
    public static boolean cascade(PluginPluginActionRequest request) {
        return request != null && Boolean.TRUE.equals(request.getCascade());
    }

    public static PluginMarketplaceBatchInstallCmd toBatchInstallCmd(PluginMarketplaceBatchInstallRequest request) {
        if (request == null || request.getItems() == null) {
            return new PluginMarketplaceBatchInstallCmd();
        }
        java.util.List<PluginMarketplaceBatchInstallCmd.Item> items = request.getItems().stream()
                .map(item -> {
                    PluginMarketplaceBatchInstallCmd.Item cmd = new PluginMarketplaceBatchInstallCmd.Item();
                    cmd.setCode(item.getCode());
                    cmd.setReleaseVersion(item.getReleaseVersion());
                    cmd.setSourceCode(item.getSourceCode());
                    return cmd;
                })
                .toList();
        PluginMarketplaceBatchInstallCmd cmd = new PluginMarketplaceBatchInstallCmd();
        cmd.setItems(items);
        return cmd;
    }

    public static PluginModuleRes toRes(PluginModuleDTO dto) {
        return PluginModuleRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .version(dto.getVersion())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .mainClass(dto.getMainClass())
                .jarPath(dto.getJarPath())
                .gitUrl(dto.getGitUrl())
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
                .marketSourceCode(dto.getMarketSourceCode())
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

    public static PluginDependentsRes toRes(online.yudream.base.application.platform.plugin.dto.PluginDependentsDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginDependentsRes.builder()
                .code(dto.getCode())
                .dependents(dto.getDependents() == null ? List.of() : dto.getDependents().stream()
                        .map(PluginWebAssembler::toRes)
                        .toList())
                .build();
    }

    public static PluginDependentRes toRes(online.yudream.base.application.platform.plugin.dto.PluginDependentsDTO.DependentDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginDependentRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .required(dto.isRequired())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
                .build();
    }

    public static PluginDependencyStatusRes toRes(online.yudream.base.application.platform.plugin.dto.PluginDependencyStatusDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginDependencyStatusRes.builder()
                .code(dto.getCode())
                .dependencies(dto.getDependencies() == null ? List.of() : dto.getDependencies().stream()
                        .map(PluginWebAssembler::toRes)
                        .toList())
                .build();
    }

    public static PluginDependencyStatusItemRes toRes(online.yudream.base.application.platform.plugin.dto.PluginDependencyStatusDTO.DependencyDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginDependencyStatusItemRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .required(dto.isRequired())
                .installed(dto.isInstalled())
                .installedVersion(dto.getInstalledVersion())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
                .storeAvailable(dto.isStoreAvailable())
                .storeVersion(dto.getStoreVersion())
                .storeSourceCode(dto.getStoreSourceCode())
                .storeSourceName(dto.getStoreSourceName())
                .build();
    }

    public static PluginMarketplaceInstallPlanRes toRes(online.yudream.base.application.platform.plugin.dto.PluginMarketplaceInstallPlanDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginMarketplaceInstallPlanRes.builder()
                .code(dto.getCode())
                .releaseVersion(dto.getReleaseVersion())
                .installable(dto.isInstallable())
                .installDisabledReason(dto.getInstallDisabledReason())
                .entries(dto.getEntries() == null ? List.of() : dto.getEntries().stream()
                        .map(PluginWebAssembler::toRes)
                        .toList())
                .build();
    }

    public static PluginMarketplaceInstallPlanEntryRes toRes(online.yudream.base.application.platform.plugin.dto.PluginMarketplaceInstallPlanDTO.EntryDTO dto) {
        if (dto == null) {
            return null;
        }
        return PluginMarketplaceInstallPlanEntryRes.builder()
                .code(dto.getCode())
                .displayName(dto.getDisplayName())
                .required(dto.isRequired())
                .range(dto.getRange())
                .installed(dto.isInstalled())
                .installedVersion(dto.getInstalledVersion())
                .versionSatisfied(dto.isVersionSatisfied())
                .storeAvailable(dto.isStoreAvailable())
                .storeVersion(dto.getStoreVersion())
                .storeSourceCode(dto.getStoreSourceCode())
                .storeSourceName(dto.getStoreSourceName())
                .installable(dto.isInstallable())
                .installDisabledReason(dto.getInstallDisabledReason())
                .build();
    }

    public static List<PluginStorePluginRes> toStoreResList(List<PluginStorePluginDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toStoreRes).toList();
    }

    public static PluginStorePluginRes toStoreRes(PluginStorePluginDTO dto) {
        return PluginStorePluginRes.builder()
                .code(dto.getCode())
                .descriptor(toStoreDescriptorRes(dto.getDescriptor()))
                .sourceCode(dto.getSourceCode())
                .sourceName(dto.getSourceName())
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
                .sourceCode(dto.getSourceCode())
                .sourceName(dto.getSourceName())
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
                .category(dto.getCategory())
                .tags(dto.getTags())
                .gitUrl(dto.getGitUrl())
                .publishedAt(dto.getPublishedAt())
                .updatedAt(dto.getUpdatedAt())
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
                .globalWidgets(dto.getGlobalWidgets() == null ? List.of() : dto.getGlobalWidgets().stream()
                        .map(PluginWebAssembler::toRes).toList())
                .build();
    }

    public static PluginGlobalWidgetRes toRes(PluginGlobalWidgetDTO dto) {
        return PluginGlobalWidgetRes.builder()
                .pluginCode(dto.getPluginCode())
                .code(dto.getCode())
                .component(dto.getComponent())
                .permission(dto.getPermission())
                .sort(dto.getSort())
                .build();
    }

    public static PluginThemeRes toThemeRes(PluginThemeDTO dto) {
        return PluginThemeRes.builder()
                .pluginCode(dto.getPluginCode())
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .scopes(dto.getScopes())
                .styles(dto.getStyles())
                .preview(dto.getPreview())
                .configSchema(dto.getConfigSchema())
                .homeComponent(dto.getHomeComponent())
                .chromeComponent(dto.getChromeComponent())
                .moduleName(dto.getModuleName())
                .assetRevision(dto.getAssetRevision())
                .build();
    }

    public static Map<String, PluginThemeRes> toActiveThemeResMap(Map<String, PluginThemeDTO> activeThemes) {
        return activeThemes.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toThemeRes(entry.getValue())));
    }

    public static PluginThemeOverviewRes toThemeOverviewRes(PluginThemeOverviewDTO dto) {
        return PluginThemeOverviewRes.builder()
                .themes(dto.getThemes() == null ? List.of() : dto.getThemes().stream()
                        .map(PluginWebAssembler::toThemeRes).toList())
                .active(dto.getActive() == null ? Map.of() : dto.getActive())
                .build();
    }

    public static PluginFrontendModuleRes toRes(PluginFrontendModuleDTO dto) {
        return PluginFrontendModuleRes.builder()
                .pluginCode(dto.getPluginCode())
                .entry(dto.getEntry())
                .moduleName(dto.getModuleName())
                .sdkVersion(dto.getSdkVersion())
                .integrity(dto.getIntegrity())
                .assetRevision(dto.getAssetRevision())
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
                .siteNav(dto.getSiteNav())
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

    public static List<PluginMessagingConnectionRes> toMessagingConnectionResList(List<PluginMessagingConnectionDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toMessagingConnectionRes).toList();
    }

    public static PluginMessagingConnectionRes toMessagingConnectionRes(PluginMessagingConnectionDTO dto) {
        return PluginMessagingConnectionRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .platform(dto.getPlatform())
                .userId(dto.getUserId())
                .protocol(dto.getProtocol())
                .build();
    }

    public static List<PluginMessagingGroupRes> toMessagingGroupResList(List<PluginMessagingGroupDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toMessagingGroupRes).toList();
    }

    public static PluginMessagingGroupRes toMessagingGroupRes(PluginMessagingGroupDTO dto) {
        return PluginMessagingGroupRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .build();
    }

    public static List<PluginUserCatalogRes> toUserCatalogResList(List<PluginUserCatalogDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toUserCatalogRes).toList();
    }

    public static PluginUserCatalogRes toUserCatalogRes(PluginUserCatalogDTO dto) {
        return PluginUserCatalogRes.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .avatar(dto.getAvatar())
                .status(dto.getStatus())
                .deptIds(dto.getDeptIds() == null ? List.of() : dto.getDeptIds())
                .deptNames(dto.getDeptNames() == null ? List.of() : dto.getDeptNames())
                .build();
    }

    public static List<PluginDeptCatalogRes> toDeptCatalogResList(List<PluginDeptCatalogDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toDeptCatalogRes).toList();
    }

    public static PluginDeptCatalogRes toDeptCatalogRes(PluginDeptCatalogDTO dto) {
        return PluginDeptCatalogRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .label(dto.getLabel())
                .parentId(dto.getParentId())
                .status(dto.getStatus())
                .children(toDeptCatalogResList(dto.getChildren()))
                .build();
    }

    public static List<PluginRoleCatalogRes> toRoleCatalogResList(List<PluginRoleCatalogDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toRoleCatalogRes).toList();
    }

    public static PluginRoleCatalogRes toRoleCatalogRes(PluginRoleCatalogDTO dto) {
        return PluginRoleCatalogRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .deptId(dto.getDeptId())
                .deptName(dto.getDeptName())
                .build();
    }

    public static List<PluginAiAgentCatalogRes> toAiAgentCatalogResList(List<PluginAiAgentCatalogDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toAiAgentCatalogRes).toList();
    }

    public static PluginAiAgentCatalogRes toAiAgentCatalogRes(PluginAiAgentCatalogDTO dto) {
        return PluginAiAgentCatalogRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }

    public static List<PluginAiProviderCatalogRes> toAiProviderCatalogResList(List<PluginAiProviderCatalogDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginWebAssembler::toAiProviderCatalogRes).toList();
    }

    public static PluginAiProviderCatalogRes toAiProviderCatalogRes(PluginAiProviderCatalogDTO dto) {
        return PluginAiProviderCatalogRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .models(dto.getModels() == null ? List.of() : dto.getModels().stream()
                        .map(model -> PluginAiModelCatalogRes.builder()
                                .code(model.getCode())
                                .name(model.getName())
                                .build())
                        .toList())
                .build();
    }

    public static String frontendAssetPath(String pluginCode, HttpServletRequest request) {
        String prefix = "/api/platform/plugins/" + pluginCode + "/assets";
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithinApplication = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        if (!pathWithinApplication.startsWith(prefix)) {
            return "";
        }
        String path = pathWithinApplication.substring(prefix.length());
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
