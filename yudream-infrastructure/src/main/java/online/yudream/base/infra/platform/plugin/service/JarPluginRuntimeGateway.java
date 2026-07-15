package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.dashboard.PluginDashboardCard;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.frontend.PluginFrontendRoute;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.command.PluginCommandContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JarPluginRuntimeGateway implements PluginRuntimeGateway {

    private final PluginProperties pluginProperties;
    private final FrameworkServices frameworkServices;
    private final PluginServiceRegistry pluginServiceRegistry;
    private final PluginAiToolRegistry aiToolRegistry;
    private final PluginSemanticMemoryService semanticMemoryService;
    private final AgentRuntimeApplicationRegistry agentApplicationRegistry;
    private final ConcurrentMap<String, PluginRuntimeHolder> holders = new ConcurrentHashMap<>();
    private final PluginAnnotationRegistrar annotationRegistrar = new PluginAnnotationRegistrar();

    @Override
    public List<PluginDescriptorInfo> discover() {
        if (!pluginProperties.isEnabled()) {
            return List.of();
        }
        return pluginProperties.getDirectories().stream()
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(this::jarFiles)
                .map(this::readDescriptor)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(PluginDescriptorInfo::code))
                .toList();
    }

    @Override
    public Optional<PluginDescriptorInfo> describe(Path jarPath) {
        if (!pluginProperties.isEnabled()) {
            return Optional.empty();
        }
        return readDescriptor(jarPath);
    }

    @Override
    public void load(PluginModule module) {
        if (!pluginProperties.isEnabled()) {
            throw new BizException("插件系统未启用");
        }
        if (holders.containsKey(module.getCode())) {
            return;
        }
        PluginRuntimeHolder holder = createHolder(module);
        holder.getPlugin().onLoad(holder.getContext());
        holders.put(module.getCode(), holder);
        log.info("Plugin loaded: code={}, jar={}", module.getCode(), module.getJarPath());
    }

    @Override
    public void enable(PluginModule module) {
        load(module);
        PluginRuntimeHolder holder = holder(module.getCode());
        if (holder.isEnabled()) {
            return;
        }
        try {
            annotationRegistrar.register(holder.getPlugin(), holder.getContext());
            registerDeclaredAgents(holder);
            holder.getPlugin().onEnable(holder.getContext());
            holder.setEnabled(true);
            log.info("Plugin enabled: code={}", module.getCode());
        } catch (RuntimeException e) {
            holder.getContext().clearRuntimeContributions();
            throw e;
        }
    }

    @Override
    public void disable(String code) {
        PluginRuntimeHolder holder = holders.get(code);
        if (holder == null || !holder.isEnabled()) {
            return;
        }
        ensureNoEnabledHardDependents(code);
        holder.getPlugin().onDisable(holder.getContext());
        holder.getContext().clearRuntimeContributions();
        holder.setEnabled(false);
        log.info("Plugin disabled: code={}", code);
    }

    @Override
    public void unload(String code) {
        ensureNoLoadedDependents(code);
        PluginRuntimeHolder holder = holders.remove(code);
        if (holder == null) {
            return;
        }
        if (holder.isEnabled()) {
            holder.getPlugin().onDisable(holder.getContext());
        }
        holder.getPlugin().onUnload(holder.getContext());
        holder.getContext().dispose();
        closeClassLoader(holder.getClassLoader());
        log.info("Plugin unloaded: code={}", code);
    }

    @Override
    public boolean loaded(String code) {
        return holders.containsKey(code);
    }

    @Override
    public boolean enabled(String code) {
        PluginRuntimeHolder holder = holders.get(code);
        return holder != null && holder.isEnabled();
    }

    @Override
    public List<PluginPermissionInfo> permissions(String code) {
        PluginRuntimeHolder holder = holders.get(code);
        if (holder == null) {
            return List.of();
        }
        return holder.getContext().permissions().stream().map(this::toInfo).toList();
    }

    @Override
    public List<PluginFrontendModuleInfo> frontendModules() {
        return holders.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .flatMap(entry -> entry.getValue().getContext().frontendModules().stream()
                        .map(module -> toInfo(entry.getKey(), module)))
                .toList();
    }

    @Override
    public List<PluginDashboardCardInfo> dashboardCards() {
        return holders.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .flatMap(entry -> entry.getValue().getContext().dashboardCards().stream()
                        .map(card -> toInfo(entry.getKey(), card)))
                .toList();
    }

    @Override
    public List<PluginHttpEndpointInfo> httpEndpoints() {
        return holders.values().stream()
                .filter(PluginRuntimeHolder::isEnabled)
                .flatMap(holder -> holder.getContext().httpEndpoints().stream())
                .sorted(Comparator.comparing(PluginHttpEndpointInfo::pluginCode)
                        .thenComparing(PluginHttpEndpointInfo::path)
                        .thenComparing(PluginHttpEndpointInfo::method))
                .toList();
    }

    public void publishMessagingEvent(PluginEvent event) {
        holders.values().stream()
                .filter(PluginRuntimeHolder::isEnabled)
                .map(PluginRuntimeHolder::getContext)
                .forEach(context -> context.interactionRegistry().publish(event,
                        "internal".equals(event.type()) || "group_request".equals(event.type())));
    }

    public String displayName(String pluginCode) {
        PluginRuntimeHolder holder = holders.get(pluginCode);
        if (holder == null || holder.getDescriptor().name() == null || holder.getDescriptor().name().isBlank()) {
            return pluginCode;
        }
        return holder.getDescriptor().name();
    }

    @Override
    public List<PluginCommandInfo> commands() {
        return holders.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .flatMap(entry -> entry.getValue().getContext().commandRegistry().registrations().stream()
                        .map(registration -> new PluginCommandInfo(entry.getKey(), registration.definition().code(),
                                registration.definition().command(), registration.definition().name(),
                                registration.definition().permission(), registration.definition().description(),
                                registration.definition().allowAnonymous())))
                .sorted(java.util.Comparator.comparing(PluginCommandInfo::pluginCode).thenComparing(PluginCommandInfo::code))
                .toList();
    }

    public void publishCommand(PluginEvent event, String command, List<String> arguments, Long userId,
                               java.util.function.Predicate<String> permissionChecker) {
        holders.values().stream()
                .filter(PluginRuntimeHolder::isEnabled)
                .map(PluginRuntimeHolder::getContext)
                .flatMap(context -> context.commandRegistry().registrations().stream())
                .filter(registration -> registration.definition().command().equalsIgnoreCase(command))
                .filter(registration -> registration.definition().allowAnonymous() || userId != null)
                .filter(registration -> registration.definition().permission().isBlank()
                        || permissionChecker.test(registration.definition().permission()))
                .forEach(registration -> {
                    try {
                        registration.handler().handle(new PluginCommandContext(event, command, arguments, userId));
                    } catch (Exception exception) {
                        log.warn("Plugin command handler failed: command={}", command, exception);
                    }
                });
    }

    @Override
    public Optional<PluginFrontendAssetInfo> frontendAsset(String code, String assetPath) {
        PluginRuntimeHolder holder = holder(code);
        if (!holder.isEnabled()) {
            throw new BizException("插件未启用");
        }
        String path = normalizeAssetPath(assetPath);
        String resourcePath = "META-INF/yudream-plugin/frontend/" + code + "/" + path;
        try (InputStream inputStream = holder.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            return Optional.of(new PluginFrontendAssetInfo(path, contentType(path), inputStream.readAllBytes()));
        } catch (IOException e) {
            throw new BizException("插件前端资源读取失败：" + e.getMessage());
        }
    }

    @Override
    public PluginHttpDispatchResult dispatch(PluginHttpDispatchRequest request) {
        PluginRuntimeHolder holder = holder(request.pluginCode());
        if (!holder.isEnabled()) {
            throw new BizException("插件未启用");
        }
        PluginHttpHandler handler = holder.getContext()
                .findHttpHandler(request.method(), request.path())
                .orElseThrow(() -> new BizException("插件接口不存在"));
        PluginHttpRequest pluginRequest = new PluginHttpRequest(
                request.method(),
                request.path(),
                request.headers(),
                request.query(),
                request.body(),
                new PluginPrincipal(request.userId(), request.permissions())
        );
        PluginHttpResponse response;
        try {
            response = handler.handle(pluginRequest);
        } catch (IllegalArgumentException e) {
            response = PluginHttpResponse.rawJson(400, Map.of("message", messageOrDefault(e, "请求参数不正确")));
        } catch (RuntimeException e) {
            Optional<IllegalArgumentException> argumentException = findCause(e, IllegalArgumentException.class);
            if (argumentException.isPresent()) {
                response = PluginHttpResponse.rawJson(400, Map.of("message", messageOrDefault(argumentException.get(), "请求参数不正确")));
            } else {
                throw e;
            }
        }
        return new PluginHttpDispatchResult(response.status(), response.headers(), response.contentType(), response.body(), response.wrapped());
    }

    private String messageOrDefault(Throwable throwable, String fallback) {
        return StringUtils.hasText(throwable.getMessage()) ? throwable.getMessage() : fallback;
    }

    private <T extends Throwable> Optional<T> findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private Stream<Path> jarFiles(Path directory) {
        try {
            return Files.list(directory)
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"));
        } catch (IOException e) {
            log.warn("Failed to scan plugin directory {}", directory, e);
            return Stream.empty();
        }
    }

    private Optional<PluginDescriptorInfo> readDescriptor(Path jarPath) {
        try {
            return Optional.of(toInfo(readYamlDescriptor(jarPath), jarPath));
        } catch (Exception e) {
            log.warn("Failed to read plugin descriptor from {}", jarPath, e);
            return Optional.empty();
        }
    }

    private PluginRuntimeHolder createHolder(PluginModule module) {
        if (!StringUtils.hasText(module.getJarPath())) {
            throw new BizException("插件 JAR 路径为空");
        }
        Path jarPath = Path.of(module.getJarPath());
        if (!Files.isRegularFile(jarPath)) {
            throw new BizException("插件 JAR 不存在：" + module.getJarPath());
        }
        try {
            PluginDescriptor descriptor = readYamlDescriptor(jarPath);
            if (!module.getCode().equals(descriptor.code())) {
                throw new BizException("插件编码不匹配：" + descriptor.code());
            }
            URLClassLoader classLoader = createClassLoader(jarPath, descriptor);
            YuDreamPlugin plugin = instantiatePlugin(classLoader, descriptor);
            return new PluginRuntimeHolder(
                    classLoader,
                    plugin,
                    descriptor,
                    new PluginContextImpl(
                            module.getCode(),
                            classLoader,
                            frameworkServices,
                            pluginServiceRegistry,
                            declaredDependencies(descriptor),
                            this::enabled,
                            aiToolRegistry,
                            semanticMemoryService,
                            agentApplicationRegistry
                    )
            );
        } catch (IOException e) {
            throw new BizException("插件 ClassLoader 创建失败：" + e.getMessage());
        }
    }

    private URLClassLoader createClassLoader(Path jarPath, PluginDescriptor descriptor) throws IOException {
        URL[] urls = new URL[]{jarPath.toUri().toURL()};
        return new PluginClassLoader(urls, getClass().getClassLoader(), dependencyClassLoaders(descriptor));
    }

    private PluginDescriptor readYamlDescriptor(Path jarPath) throws IOException {
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.jar.JarEntry entry = jarFile.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new BizException("插件 JAR 缺少 plugin.yml");
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                return new PluginYamlDescriptorReader().read(inputStream);
            }
        }
    }

    private void registerDeclaredAgents(PluginRuntimeHolder holder) {
        ClassLoader classLoader = holder.getClassLoader();
        try (InputStream manifest = classLoader.getResourceAsStream("plugin.yml")) {
            List<PluginAgentManifestReader.Definition> definitions = new PluginAgentManifestReader().read(manifest);
            for (PluginAgentManifestReader.Definition definition : definitions) {
                String resource = safeAgentResource(definition.workflowResource());
                try (InputStream workflow = classLoader.getResourceAsStream(resource)) {
                    if (workflow == null) {
                        throw new BizException("插件 Agent 工作流资源不存在：" + resource);
                    }
                    holder.getContext().registerDeclaredAgent(
                            definition,
                            new String(workflow.readAllBytes(), StandardCharsets.UTF_8)
                    );
                }
            }
        } catch (IOException exception) {
            throw new BizException("读取插件 Agent 定义失败：" + exception.getMessage());
        }
    }

    private String safeAgentResource(String resource) {
        String normalized = resource == null ? "" : resource.trim().replace('\\', '/');
        if (!StringUtils.hasText(normalized) || normalized.startsWith("/") || normalized.contains("../")) {
            throw new BizException("插件 Agent 工作流资源路径无效：" + resource);
        }
        return normalized;
    }

    private YuDreamPlugin instantiatePlugin(URLClassLoader classLoader, PluginDescriptor descriptor) {
        try {
            Class<?> pluginClass = classLoader.loadClass(descriptor.mainClass());
            if (!YuDreamPlugin.class.isAssignableFrom(pluginClass)) {
                throw new BizException("plugin.yml 的 main 必须实现 YuDreamPlugin: " + descriptor.mainClass());
            }
            return (YuDreamPlugin) pluginClass.getDeclaredConstructor().newInstance();
        } catch (BizException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new BizException("插件主类初始化失败: " + descriptor.mainClass() + ", " + e.getMessage());
        }
    }

    private List<ClassLoader> dependencyClassLoaders(PluginDescriptor descriptor) {
        List<ClassLoader> classLoaders = new ArrayList<>();
        for (String dependencyCode : descriptor.dependencies()) {
            PluginRuntimeHolder dependency = holders.get(dependencyCode);
            if (dependency == null || !dependency.isEnabled()) {
                throw new BizException("硬依赖插件未启用: " + dependencyCode);
            }
            classLoaders.add(dependency.getClassLoader());
        }
        for (String dependencyCode : descriptor.softDependencies()) {
            PluginRuntimeHolder dependency = holders.get(dependencyCode);
            if (dependency != null && dependency.isEnabled()) {
                classLoaders.add(dependency.getClassLoader());
            }
        }
        return classLoaders;
    }

    private Set<String> declaredDependencies(PluginDescriptor descriptor) {
        Set<String> dependencies = new java.util.LinkedHashSet<>(descriptor.dependencies());
        dependencies.addAll(descriptor.softDependencies());
        return dependencies;
    }

    private void ensureNoEnabledHardDependents(String code) {
        holders.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .filter(entry -> entry.getValue().getDescriptor().dependencies().contains(code))
                .findFirst()
                .ifPresent(entry -> {
                    throw new BizException("请先禁用依赖插件: " + entry.getKey());
                });
    }

    private void ensureNoLoadedDependents(String code) {
        holders.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(code))
                .filter(entry -> entry.getValue().getDescriptor().dependencies().contains(code)
                        || entry.getValue().getDescriptor().softDependencies().contains(code))
                .findFirst()
                .ifPresent(entry -> {
                    throw new BizException("请先卸载依赖插件: " + entry.getKey());
                });
    }

    private PluginRuntimeHolder holder(String code) {
        PluginRuntimeHolder holder = holders.get(code);
        if (holder == null) {
            throw new BizException("插件未加载");
        }
        return holder;
    }

    private PluginDescriptorInfo toInfo(PluginDescriptor descriptor, Path jarPath) {
        return new PluginDescriptorInfo(
                descriptor.code(),
                descriptor.name(),
                descriptor.version(),
                descriptor.description(),
                descriptor.mainClass(),
                jarPath.toAbsolutePath().normalize().toString(),
                descriptor.dependencies(),
                descriptor.softDependencies()
        );
    }

    private PluginPermissionInfo toInfo(PluginPermissionItem item) {
        return new PluginPermissionInfo(item.code(), item.name(), item.module(), item.description());
    }

    private PluginFrontendModuleInfo toInfo(String pluginCode, PluginFrontendModule module) {
        return new PluginFrontendModuleInfo(
                pluginCode,
                module.entry(),
                module.moduleName(),
                module.sdkVersion(),
                module.integrity(),
                module.menuTitle(),
                module.menuIcon(),
                module.menuSort(),
                module.routes().stream().map(this::toInfo).toList(),
                module.parentCode(),
                true,
                MenuStatus.ACTIVE
        );
    }

    private PluginFrontendRouteInfo toInfo(PluginFrontendRoute route) {
        return new PluginFrontendRouteInfo(
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
                route.sort(),
                route.hideInMenu()
        );
    }

    private PluginDashboardCardInfo toInfo(String pluginCode, PluginDashboardCard card) {
        return new PluginDashboardCardInfo(
                pluginCode,
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
        );
    }

    private String normalizeAssetPath(String assetPath) {
        String path = assetPath == null ? "" : assetPath.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!StringUtils.hasText(path) || path.contains("..")) {
            throw new BizException("插件前端资源路径非法");
        }
        return path;
    }

    private String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
            return "text/javascript;charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (lower.endsWith(".json") || lower.endsWith(".map")) {
            return "application/json;charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (lower.endsWith(".woff")) {
            return "font/woff";
        }
        return "application/octet-stream";
    }

    private void closeClassLoader(URLClassLoader classLoader) {
        try {
            classLoader.close();
        } catch (IOException e) {
            log.warn("Failed to close plugin classloader", e);
        }
    }
}
