package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.dashboard.PluginDashboardCard;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PluginContextImpl implements PluginContext {

    private final String pluginCode;
    private final FrameworkServices frameworkServices;
    private final PluginExtensionRegistry pluginExtensionRegistry;
    private final List<PluginMenuItem> menus = new ArrayList<>();
    private final List<PluginPermissionItem> permissions = new ArrayList<>();
    private final List<PluginCapabilityItem> capabilities = new ArrayList<>();
    private final List<PluginDashboardCard> dashboardCards = new ArrayList<>();
    private final List<PluginFrontendModule> frontendModules = new ArrayList<>();
    private final List<PluginHttpEndpointInfo> httpEndpoints = new ArrayList<>();
    private final Map<String, PluginHttpHandler> httpHandlers = new ConcurrentHashMap<>();
    private final List<AutoCloseable> disposables = new ArrayList<>();
    private final Set<String> menuKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> permissionKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> capabilityKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> dashboardCardKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> frontendModuleKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> frontendRouteKeys = ConcurrentHashMap.newKeySet();
    private final PluginAnnotationRegistrar annotationRegistrar = new PluginAnnotationRegistrar();

    public PluginContextImpl(String pluginCode, FrameworkServices frameworkServices, PluginExtensionRegistry pluginExtensionRegistry) {
        this.pluginCode = pluginCode;
        this.frameworkServices = frameworkServices;
        this.pluginExtensionRegistry = pluginExtensionRegistry;
    }

    @Override
    public String pluginCode() {
        return pluginCode;
    }

    @Override
    public FrameworkServices framework() {
        return frameworkServices;
    }

    @Override
    public void registerMenu(PluginMenuItem item) {
        String key = normalizePath(requireText(item.path(), "插件菜单路径不能为空"));
        ensureUnique(menuKeys, key, "插件菜单重复：" + key);
        menus.add(item);
    }

    @Override
    public void registerPermission(PluginPermissionItem item) {
        String key = requireText(item.code(), "插件权限编码不能为空");
        ensureUnique(permissionKeys, key, "插件权限重复：" + key);
        permissions.add(item);
    }

    @Override
    public void registerCapability(PluginCapabilityItem item) {
        String key = requireText(item.code(), "插件能力编码不能为空");
        ensureUnique(capabilityKeys, key, "插件能力重复：" + key);
        capabilities.add(item);
    }

    @Override
    public void registerDashboardCard(PluginDashboardCard card) {
        String key = requireText(card.code(), "插件首页卡片编码不能为空");
        ensureUnique(dashboardCardKeys, key, "插件首页卡片重复：" + key);
        dashboardCards.add(card);
    }

    @Override
    public void registerFrontend(PluginFrontendModule module) {
        String moduleName = requireText(module.moduleName(), "插件前端模块名称不能为空");
        ensureUnique(frontendModuleKeys, moduleName, "插件前端模块重复：" + moduleName);
        for (var route : module.routes()) {
            String pathKey = normalizePath(requireText(route.path(), "插件前端路由路径不能为空"));
            String nameKey = requireText(route.name(), "插件前端路由名称不能为空");
            ensureUnique(frontendRouteKeys, "path:" + pathKey, "插件前端路由路径重复：" + pathKey);
            ensureUnique(frontendRouteKeys, "name:" + nameKey, "插件前端路由名称重复：" + nameKey);
        }
        frontendModules.add(withDefaultFrontendEntry(module, moduleName));
    }

    @Override
    public void registerHttpHandler(String method, String path, PluginHttpHandler handler) {
        registerHttpHandler(method, path, "", true, handler);
    }

    public void registerHttpHandler(String method, String path, String permission, boolean wrapResult, PluginHttpHandler handler) {
        String key = httpKey(method, path);
        if (httpHandlers.putIfAbsent(key, handler) != null) {
            throw new BizException("插件 HTTP 端点重复：" + key);
        }
        httpEndpoints.add(new PluginHttpEndpointInfo(pluginCode, method, path, permission, wrapResult));
    }

    @Override
    public void registerHttpController(Object controller) {
        if (controller == null) {
            throw new BizException("插件 HTTP Controller 不能为空");
        }
        annotationRegistrar.registerHttpEndpoints(controller, controller.getClass(), this);
    }

    @Override
    public <T> void registerExtension(Class<T> type, T extension) {
        pluginExtensionRegistry.register(pluginCode, type, extension);
    }

    @Override
    public <T> Optional<T> getExtension(Class<T> type) {
        return pluginExtensionRegistry.find(pluginCode, type);
    }

    @Override
    public void onDispose(AutoCloseable closeable) {
        disposables.add(closeable);
    }

    public List<PluginPermissionItem> permissions() {
        return List.copyOf(permissions);
    }

    public List<PluginFrontendModule> frontendModules() {
        return List.copyOf(frontendModules);
    }

    public List<PluginDashboardCard> dashboardCards() {
        return List.copyOf(dashboardCards);
    }

    public List<PluginHttpEndpointInfo> httpEndpoints() {
        return List.copyOf(httpEndpoints);
    }

    public Optional<PluginHttpHandler> findHttpHandler(String method, String path) {
        String normalizedMethod = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "*";
        String normalizedPath = normalizePath(path);
        PluginHttpHandler handler = httpHandlers.get(httpKey(normalizedMethod, normalizedPath));
        if (handler == null) {
            handler = httpHandlers.get(httpKey("*", normalizedPath));
        }
        if (handler != null) {
            return Optional.of(handler);
        }
        return httpHandlers.entrySet().stream()
                .filter(entry -> routeMatches(entry.getKey(), normalizedMethod, normalizedPath))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public void clearRuntimeContributions() {
        closeDisposables();
        menus.clear();
        permissions.clear();
        capabilities.clear();
        dashboardCards.clear();
        frontendModules.clear();
        httpEndpoints.clear();
        httpHandlers.clear();
        pluginExtensionRegistry.clear(pluginCode);
        menuKeys.clear();
        permissionKeys.clear();
        capabilityKeys.clear();
        dashboardCardKeys.clear();
        frontendModuleKeys.clear();
        frontendRouteKeys.clear();
    }

    public void dispose() {
        clearRuntimeContributions();
    }

    private void closeDisposables() {
        for (AutoCloseable disposable : disposables) {
            try {
                disposable.close();
            } catch (Exception ignored) {
            }
        }
        disposables.clear();
    }

    private String httpKey(String method, String path) {
        String safeMethod = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "*";
        return safeMethod + " " + normalizePath(path);
    }

    private boolean routeMatches(String key, String method, String path) {
        int splitIndex = key.indexOf(' ');
        if (splitIndex < 0) {
            return false;
        }
        String routeMethod = key.substring(0, splitIndex);
        String routePath = key.substring(splitIndex + 1);
        if (!"*".equals(routeMethod) && !routeMethod.equals(method)) {
            return false;
        }
        return pathMatches(routePath, path);
    }

    private boolean pathMatches(String routePath, String path) {
        String[] routeSegments = trimSlashes(routePath).split("/");
        String[] pathSegments = trimSlashes(path).split("/");
        if (routeSegments.length == 1 && routeSegments[0].isBlank()) {
            return pathSegments.length == 1 && pathSegments[0].isBlank();
        }
        int routeIndex = 0;
        int pathIndex = 0;
        while (routeIndex < routeSegments.length && pathIndex < pathSegments.length) {
            String routeSegment = routeSegments[routeIndex];
            if ("**".equals(routeSegment)) {
                return true;
            }
            String pathSegment = pathSegments[pathIndex];
            if (!isPathVariable(routeSegment) && !"*".equals(routeSegment) && !routeSegment.equals(pathSegment)) {
                return false;
            }
            routeIndex++;
            pathIndex++;
        }
        return routeIndex == routeSegments.length && pathIndex == pathSegments.length;
    }

    private boolean isPathVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2;
    }

    private String trimSlashes(String value) {
        String result = value == null ? "" : value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private PluginFrontendModule withDefaultFrontendEntry(PluginFrontendModule module, String moduleName) {
        String entry = StringUtils.hasText(module.entry()) ? module.entry().trim() : defaultFrontendEntry();
        return new PluginFrontendModule(
                entry,
                moduleName,
                module.sdkVersion(),
                module.integrity(),
                module.menuTitle(),
                module.menuIcon(),
                module.menuSort(),
                module.routes()
        );
    }

    private String defaultFrontendEntry() {
        return "/api/platform/plugins/" + pluginCode + "/assets/remoteEntry.js";
    }

    private void ensureUnique(Set<String> keys, String key, String message) {
        if (!keys.add(key)) {
            throw new BizException(message);
        }
    }
}
