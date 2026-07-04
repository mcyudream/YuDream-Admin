package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.domain.common.exception.BizException;
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
    private final List<PluginMenuItem> menus = new ArrayList<>();
    private final List<PluginPermissionItem> permissions = new ArrayList<>();
    private final List<PluginCapabilityItem> capabilities = new ArrayList<>();
    private final List<PluginFrontendModule> frontendModules = new ArrayList<>();
    private final Map<String, PluginHttpHandler> httpHandlers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> extensions = new ConcurrentHashMap<>();
    private final List<AutoCloseable> disposables = new ArrayList<>();
    private final Set<String> menuKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> permissionKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> capabilityKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> frontendModuleKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> frontendRouteKeys = ConcurrentHashMap.newKeySet();

    public PluginContextImpl(String pluginCode, FrameworkServices frameworkServices) {
        this.pluginCode = pluginCode;
        this.frameworkServices = frameworkServices;
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
    public void registerFrontend(PluginFrontendModule module) {
        String moduleName = requireText(module.moduleName(), "插件前端模块名称不能为空");
        ensureUnique(frontendModuleKeys, moduleName, "插件前端模块重复：" + moduleName);
        for (var route : module.routes()) {
            String pathKey = normalizePath(requireText(route.path(), "插件前端路由路径不能为空"));
            String nameKey = requireText(route.name(), "插件前端路由名称不能为空");
            ensureUnique(frontendRouteKeys, "path:" + pathKey, "插件前端路由路径重复：" + pathKey);
            ensureUnique(frontendRouteKeys, "name:" + nameKey, "插件前端路由名称重复：" + nameKey);
        }
        frontendModules.add(module);
    }

    @Override
    public void registerHttpHandler(String method, String path, PluginHttpHandler handler) {
        String key = httpKey(method, path);
        if (httpHandlers.putIfAbsent(key, handler) != null) {
            throw new BizException("插件 HTTP 端点重复：" + key);
        }
    }

    @Override
    public <T> void registerExtension(Class<T> type, T extension) {
        extensions.put(type, extension);
    }

    @Override
    public <T> Optional<T> getExtension(Class<T> type) {
        Object extension = extensions.get(type);
        return extension == null ? Optional.empty() : Optional.of(type.cast(extension));
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

    public Optional<PluginHttpHandler> findHttpHandler(String method, String path) {
        PluginHttpHandler handler = httpHandlers.get(httpKey(method, path));
        if (handler == null) {
            handler = httpHandlers.get(httpKey("*", path));
        }
        return Optional.ofNullable(handler);
    }

    public void clearRuntimeContributions() {
        closeDisposables();
        menus.clear();
        permissions.clear();
        capabilities.clear();
        frontendModules.clear();
        httpHandlers.clear();
        extensions.clear();
        menuKeys.clear();
        permissionKeys.clear();
        capabilityKeys.clear();
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

    private void ensureUnique(Set<String> keys, String key, String message) {
        if (!keys.add(key)) {
            throw new BizException(message);
        }
    }
}
