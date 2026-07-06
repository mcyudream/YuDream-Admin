package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.annotation.PluginCapability;
import online.yudream.base.plugin.spi.annotation.PluginConfigEntry;
import online.yudream.base.plugin.spi.annotation.PluginDashboardCard;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.annotation.PluginMenu;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.capability.PluginCapabilityItem;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.frontend.PluginFrontendRoute;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.menu.PluginMenuItem;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class PluginAnnotationRegistrar {

    void register(YuDreamPlugin plugin, PluginContextImpl context) {
        Class<?> pluginClass = plugin.getClass();
        registerPermissions(pluginClass, context);
        registerMenus(pluginClass, context);
        registerCapabilities(pluginClass, context);
        registerDashboardCards(pluginClass, context);
        registerFrontend(pluginClass, context);
        registerHttpEndpoints(plugin, pluginClass, context);
    }

    private void registerPermissions(Class<?> pluginClass, PluginContextImpl context) {
        for (PluginPermission permission : pluginClass.getAnnotationsByType(PluginPermission.class)) {
            context.registerPermission(new PluginPermissionItem(
                    permission.code(),
                    permission.name(),
                    permission.module(),
                    permission.description()
            ));
        }
    }

    private void registerMenus(Class<?> pluginClass, PluginContextImpl context) {
        for (PluginMenu menu : pluginClass.getAnnotationsByType(PluginMenu.class)) {
            context.registerMenu(new PluginMenuItem(
                    menu.title(),
                    menu.path(),
                    menu.icon(),
                    menu.permission(),
                    menu.parentPath(),
                    menu.sort()
            ));
        }
    }

    private void registerCapabilities(Class<?> pluginClass, PluginContextImpl context) {
        for (PluginCapability capability : pluginClass.getAnnotationsByType(PluginCapability.class)) {
            context.registerCapability(toCapabilityItem(capability));
        }
    }

    private PluginCapabilityItem toCapabilityItem(PluginCapability capability) {
        return new PluginCapabilityItem(
                capability.code(),
                capability.name(),
                capability.type(),
                capability.description(),
                capability.icon(),
                Arrays.stream(capability.defaultConfig()).collect(Collectors.toMap(
                        PluginConfigEntry::key,
                        PluginConfigEntry::value,
                        (left, right) -> right
                )),
                List.of(capability.dependencies())
        );
    }

    private void registerDashboardCards(Class<?> pluginClass, PluginContextImpl context) {
        for (PluginDashboardCard card : pluginClass.getAnnotationsByType(PluginDashboardCard.class)) {
            context.registerDashboardCard(new online.yudream.base.plugin.spi.dashboard.PluginDashboardCard(
                    card.code(),
                    card.title(),
                    card.description(),
                    card.icon(),
                    card.category(),
                    card.permission(),
                    card.component(),
                    card.actionPath(),
                    card.dragPayloadTemplate(),
                    card.tone(),
                    card.defaultW(),
                    card.defaultH(),
                    card.minW(),
                    card.minH(),
                    card.sort()
            ));
        }
    }

    private void registerFrontend(Class<?> pluginClass, PluginContextImpl context) {
        PluginFrontend frontend = pluginClass.getAnnotation(PluginFrontend.class);
        if (frontend == null) {
            return;
        }
        context.registerFrontend(new PluginFrontendModule(
                frontend.entry(),
                frontend.moduleName(),
                frontend.sdkVersion(),
                frontend.integrity(),
                frontend.menuTitle(),
                frontend.menuIcon(),
                frontend.menuSort(),
                Arrays.stream(frontend.routes()).map(this::toRoute).toList()
        ));
    }

    private PluginFrontendRoute toRoute(PluginRoute route) {
        return new PluginFrontendRoute(
                route.path(),
                route.name(),
                route.title(),
                route.icon(),
                route.parentPath(),
                route.parentTitle(),
                route.parentIcon(),
                route.parentSort(),
                route.component(),
                route.permission(),
                route.sort()
        );
    }

    void registerHttpEndpoints(Object target, Class<?> targetClass, PluginContextImpl context) {
        Class<?> current = targetClass;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                PluginHttpEndpoint endpoint = method.getAnnotation(PluginHttpEndpoint.class);
                if (endpoint == null) {
                    continue;
                }
                validateEndpointMethod(method);
                method.setAccessible(true);
                context.registerHttpHandler(endpoint.method(), endpoint.path(), request -> {
                    if (StringUtils.hasText(endpoint.permission())) {
                        context.framework().security().requirePermission(request.principal(), endpoint.permission());
                    }
                    return invokeEndpoint(target, method, request, context, endpoint.wrapResult());
                });
            }
            current = current.getSuperclass();
        }
    }

    private void validateEndpointMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 2) {
            throw new BizException("插件 HTTP 端点方法最多只能声明 PluginHttpRequest 和 PluginContext：" + method.getName());
        }
        boolean requestSeen = false;
        boolean contextSeen = false;
        for (Class<?> parameterType : parameterTypes) {
            if (PluginHttpRequest.class.equals(parameterType)) {
                if (requestSeen) {
                    throw new BizException("插件 HTTP 端点方法重复声明 PluginHttpRequest：" + method.getName());
                }
                requestSeen = true;
            } else if (PluginContext.class.equals(parameterType)) {
                if (contextSeen) {
                    throw new BizException("插件 HTTP 端点方法重复声明 PluginContext：" + method.getName());
                }
                contextSeen = true;
            } else {
                throw new BizException("插件 HTTP 端点方法参数只支持 PluginHttpRequest 和 PluginContext：" + method.getName());
            }
        }
        if (Void.TYPE.equals(method.getReturnType())) {
            throw new BizException("插件 HTTP 端点方法必须返回 PluginHttpResponse 或响应对象：" + method.getName());
        }
    }

    private PluginHttpResponse invokeEndpoint(Object targetInstance, Method method, PluginHttpRequest request, PluginContext context, boolean wrapResult) {
        try {
            Object target = Modifier.isStatic(method.getModifiers()) ? null : targetInstance;
            Object result = method.invoke(target, endpointArgs(method, request, context));
            if (result instanceof PluginHttpResponse response) {
                return wrapResult ? response : response.withWrapped(false);
            }
            return PluginHttpResponse.ok(result == null ? Map.of() : result).withWrapped(wrapResult);
        } catch (IllegalAccessException e) {
            throw new BizException("插件 HTTP 端点不可访问：" + method.getName());
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BizException("插件 HTTP 端点执行失败：" + target.getMessage());
        }
    }

    private Object[] endpointArgs(Method method, PluginHttpRequest request, PluginContext context) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = PluginHttpRequest.class.equals(parameterTypes[i]) ? request : context;
        }
        return args;
    }
}
