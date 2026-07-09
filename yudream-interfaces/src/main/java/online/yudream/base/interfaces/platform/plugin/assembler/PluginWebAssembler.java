package online.yudream.base.interfaces.platform.plugin.assembler;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.application.platform.plugin.cmd.PluginFrontendRouteSortSaveCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginFrontendSortSaveCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginHttpDispatchCmd;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendManifestDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendRouteDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.interfaces.platform.plugin.request.PluginFrontendRouteSortSaveRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginFrontendSortSaveRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendManifestRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendRouteRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
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
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .loadedAt(dto.getLoadedAt())
                .enabledAt(dto.getEnabledAt())
                .loaded(dto.isLoaded())
                .enabled(dto.isEnabled())
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
                .build();
    }

    public static PluginFrontendSortSaveCmd toCmd(PluginFrontendSortSaveRequest request) {
        return PluginFrontendSortSaveCmd.builder()
                .moduleName(request.getModuleName())
                .menuSort(request.getMenuSort())
                .routes(request.getRoutes() == null
                        ? List.of()
                        : request.getRoutes().stream().map(PluginWebAssembler::toCmd).toList())
                .build();
    }

    private static PluginFrontendRouteSortSaveCmd toCmd(PluginFrontendRouteSortSaveRequest request) {
        return PluginFrontendRouteSortSaveCmd.builder()
                .path(request.getPath())
                .name(request.getName())
                .sort(request.getSort())
                .parentSort(request.getParentSort())
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
