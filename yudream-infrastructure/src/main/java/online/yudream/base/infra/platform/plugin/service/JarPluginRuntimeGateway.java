package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.enumerate.PluginDevProjectSource;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginAiToolInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCapabilityAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMessageInteractionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAgentInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.infra.platform.plugin.devmode.DevModeEnvironment;
import online.yudream.base.infra.platform.plugin.devmode.PluginDevProjectCatalog;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.dashboard.PluginDashboardCard;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.frontend.PluginFrontendRoute;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.command.PluginCommandContext;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.stream.Collectors;
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
    private final ApplicationEventPublisher eventPublisher;
    private final PluginDevModeProperties devModeProperties;
    private final PluginDevProjectCatalog devProjectCatalog;
    private final DevModeEnvironment devModeEnvironment;
    private final ConcurrentMap<String, PluginRuntimeHolder> holders = new ConcurrentHashMap<>();
    private final PluginAnnotationRegistrar annotationRegistrar = new PluginAnnotationRegistrar();

    @Override
    public List<PluginDescriptorInfo> discover() {
        if (!pluginProperties.isEnabled()) {
            return List.of();
        }
        Map<String, PluginDescriptorInfo> discovered = new java.util.LinkedHashMap<>();
        pluginProperties.getDirectories().stream()
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(this::jarFiles)
                .map(this::readDescriptor)
                .flatMap(Optional::stream)
                .forEach(descriptor -> discovered.put(descriptor.code(), descriptor));
        // 开发模式项目与同 code 的 JAR 并存时以源码目录为准，保证改动即时生效
        for (PluginDevProjectCatalog.CatalogEntry entry : devModeProjectsInternal()) {
            readDevDescriptor(entry.project()).ifPresent(descriptor -> discovered.put(descriptor.code(), descriptor));
        }
        return discovered.values().stream()
                .sorted(Comparator.comparing(PluginDescriptorInfo::code))
                .toList();
    }

    private Optional<PluginDescriptorInfo> readDevDescriptor(PluginDevModeProperties.DevProject project) {
        try {
            Path classesDir = project.classesDir();
            if (!Files.isDirectory(classesDir)) {
                return Optional.empty();
            }
            PluginDescriptor descriptor = readYamlDescriptor(classesDir);
            if (!project.getCode().trim().equals(descriptor.code())) {
                log.warn("Dev-mode project code mismatch: configured={}, plugin.yml={}", project.getCode(), descriptor.code());
                return Optional.empty();
            }
            return Optional.of(toInfo(descriptor, classesDir));
        } catch (Exception e) {
            log.warn("Failed to read dev-mode plugin descriptor for {}", project.getCode(), e);
            return Optional.empty();
        }
    }

    private List<PluginDevProjectCatalog.CatalogEntry> devModeProjectsInternal() {
        if (!devModeProperties.effectiveEnabled(devModeEnvironment)) {
            return List.of();
        }
        return devProjectCatalog.projects();
    }

    private PluginDevModeProperties.DevProject findDevProject(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return devModeProjectsInternal().stream()
                .map(PluginDevProjectCatalog.CatalogEntry::project)
                .filter(project -> code.trim().equals(project.getCode().trim()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean devModePlugin(String code) {
        return findDevProject(code) != null;
    }

    @Override
    public boolean devModeEnabled() {
        return devModeProperties.effectiveEnabled(devModeEnvironment);
    }

    @Override
    public List<PluginDevProjectInfo> devModeProjects() {
        return devModeProjectsInternal().stream()
                .map(entry -> toDevProjectInfo(entry.project(), entry.source()))
                .toList();
    }

    @Override
    public List<PluginDevProjectInfo> managedDevProjects() {
        return devProjectCatalog.projects().stream()
                .map(entry -> toDevProjectInfo(entry.project(), entry.source()))
                .toList();
    }

    @Override
    public String hostRunMode() {
        return devModeEnvironment.hostRunMode();
    }

    @Override
    public boolean devModeAutoDetected() {
        return devModeProperties.autoDetected();
    }

    @Override
    public String devProjectStoreFile() {
        return devProjectCatalog.storeFile().toString();
    }

    @Override
    public PluginDevProjectInfo registerDevProject(String code, String path, String frontendDist,
                                                   boolean autoCompile, String compileCommand) {
        PluginDevModeProperties.DevProject project = new PluginDevModeProperties.DevProject();
        project.setCode(StringUtils.hasText(code) ? code.trim() : null);
        project.setPath(path);
        project.setFrontendDist(frontendDist);
        project.setAutoCompile(autoCompile);
        if (StringUtils.hasText(compileCommand)) {
            project.setCompileCommand(compileCommand.trim());
        }
        PluginDevModeProperties.DevProject saved = devProjectCatalog.add(project);
        return toDevProjectInfo(saved, PluginDevProjectSource.FILE);
    }

    @Override
    public void removeDevProject(String code) {
        devProjectCatalog.remove(code);
    }

    private PluginDevProjectInfo toDevProjectInfo(PluginDevModeProperties.DevProject project,
                                                  PluginDevProjectSource source) {
        Path root = Path.of(project.getPath()).toAbsolutePath().normalize();
        return new PluginDevProjectInfo(project.getCode().trim(),
                root.toString(),
                project.resolvedFrontendDist().toString(),
                project.isAutoCompile(),
                source,
                Files.isDirectory(root),
                Files.isDirectory(project.classesDir()),
                Files.isRegularFile(project.classesDir().resolve("plugin.yml"))
                        || Files.isRegularFile(root.resolve("src").resolve("main")
                        .resolve("resources").resolve("plugin.yml")));
    }

    @Override
    public PluginCommandTestResult testCommand(String pluginCode, String command, List<String> arguments, String content) {
        if (!StringUtils.hasText(command)) {
            throw new BizException("指令名不能为空");
        }
        PluginRuntimeHolder holder = holder(pluginCode);
        if (!holder.isEnabled()) {
            throw new BizException("插件未启用，无法模拟指令");
        }
        String commandName = command.trim();
        List<String> args = arguments == null ? List.of() : List.copyOf(arguments);
        List<PluginCommandRegistryImpl.Registration> registrations = holder.getContext().commandRegistry()
                .registrations().stream()
                .filter(registration -> registration.definition().command().equalsIgnoreCase(commandName))
                .toList();
        long startNanos = System.nanoTime();
        if (registrations.isEmpty()) {
            return PluginCommandTestResult.notMatched(pluginCode, commandName, elapsedMillis(startNanos));
        }
        // 模拟事件仅填充指令调试所需字段，platform 标记为 devtools，handler 若访问真实连接字段需自行判空
        PluginEvent debugEvent = new PluginEvent(null, "command-test", "devtools", null, null,
                StringUtils.hasText(content) ? content : "/" + commandName + (args.isEmpty() ? "" : " " + String.join(" ", args)),
                null, commandName, Map.of(), null, null, null, null, null);
        try {
            for (PluginCommandRegistryImpl.Registration registration : registrations) {
                registration.handler().handle(new PluginCommandContext(debugEvent, commandName, args, null));
            }
            return PluginCommandTestResult.succeeded(pluginCode, commandName, elapsedMillis(startNanos));
        } catch (Exception e) {
            return PluginCommandTestResult.failed(pluginCode, commandName,
                    e.getMessage() == null ? "指令处理异常：" + e.getClass().getSimpleName() : e.getMessage(),
                    elapsedMillis(startNanos));
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
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
        long startNanos = System.nanoTime();
        try {
            PluginRuntimeHolder holder = createHolder(module);
            try {
                holder.getPlugin().onLoad(holder.getContext());
            } catch (RuntimeException | Error e) {
                holder.getContext().dispose();
                closeClassLoader(holder.getClassLoader());
                throw e;
            }
            holders.put(module.getCode(), holder);
            log.info("Plugin loaded: code={}, jar={}", module.getCode(), module.getJarPath());
            publishLifecycle(module.getCode(), PluginLifecycleAction.LOAD, holder.getDescriptor().version(), startNanos, null);
        } catch (RuntimeException | Error e) {
            publishLifecycle(module.getCode(), PluginLifecycleAction.LOAD, null, startNanos, e);
            throw e;
        }
    }

    @Override
    public void enable(PluginModule module) {
        load(module);
        PluginRuntimeHolder holder = holder(module.getCode());
        if (holder.isEnabled()) {
            return;
        }
        long startNanos = System.nanoTime();
        try {
            annotationRegistrar.register(holder.getPlugin(), holder.getContext());
            registerDeclaredAgents(holder);
            holder.getPlugin().onEnable(holder.getContext());
            holder.setEnabled(true);
            log.info("Plugin enabled: code={}", module.getCode());
            publishLifecycle(module.getCode(), PluginLifecycleAction.ENABLE, holder.getDescriptor().version(), startNanos, null);
        } catch (RuntimeException | Error e) {
            // JAR 损坏等情况会抛 ZipError 等非 RuntimeException，必须同样回滚已注册的菜单/指令等贡献，
            // 否则残留注册会让后续重试永远报“插件指令编码重复”，只能重启 JVM 恢复
            holder.getContext().clearRuntimeContributions();
            publishLifecycle(module.getCode(), PluginLifecycleAction.ENABLE, holder.getDescriptor().version(), startNanos, e);
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
        long startNanos = System.nanoTime();
        try {
            holder.getPlugin().onDisable(holder.getContext());
            holder.getContext().clearRuntimeContributions();
            holder.setEnabled(false);
            log.info("Plugin disabled: code={}", code);
            publishLifecycle(code, PluginLifecycleAction.DISABLE, holder.getDescriptor().version(), startNanos, null);
        } catch (RuntimeException | Error e) {
            publishLifecycle(code, PluginLifecycleAction.DISABLE, holder.getDescriptor().version(), startNanos, e);
            throw e;
        }
    }

    @Override
    public void unload(String code) {
        ensureNoLoadedDependents(code);
        PluginRuntimeHolder holder = holders.remove(code);
        if (holder == null) {
            return;
        }
        long startNanos = System.nanoTime();
        String version = holder.getDescriptor() == null ? null : holder.getDescriptor().version();
        try {
            if (holder.isEnabled()) {
                holder.getPlugin().onDisable(holder.getContext());
            }
            holder.getPlugin().onUnload(holder.getContext());
            holder.getContext().dispose();
            closeClassLoader(holder.getClassLoader());
            log.info("Plugin unloaded: code={}", code);
            publishLifecycle(code, PluginLifecycleAction.UNLOAD, version, startNanos, null);
        } catch (RuntimeException | Error e) {
            publishLifecycle(code, PluginLifecycleAction.UNLOAD, version, startNanos, e);
            throw e;
        }
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
        List<PluginPermissionInfo> result = new ArrayList<>(holder.getContext().permissions().stream().map(this::toInfo).toList());
        Set<String> declared = result.stream().map(PluginPermissionInfo::code).collect(Collectors.toSet());
        // 插件 AI 工具描述的 permissionCode 同样纳入权限同步，否则工作流鉴权会因权限未注册而拒绝发布
        for (PluginAiTool tool : aiToolRegistry.tools(code)) {
            PluginAiToolDescriptor descriptor = tool == null ? null : tool.descriptor();
            if (descriptor == null || descriptor.permissionCode() == null || descriptor.permissionCode().isBlank()
                    || !declared.add(descriptor.permissionCode())) {
                continue;
            }
            String title = descriptor.title() == null || descriptor.title().isBlank() ? descriptor.name() : descriptor.title();
            result.add(new PluginPermissionInfo(descriptor.permissionCode(), title + "（工具）", "平台插件",
                    "调用插件工具 " + descriptor.name() + "：" + (descriptor.description() == null ? "" : descriptor.description())));
        }
        return List.copyOf(result);
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

    @Override
    public PluginRuntimeAssets runtimeAssets(String code) {
        PluginRuntimeHolder holder = holders.get(code);
        if (holder == null) {
            return PluginRuntimeAssets.unloaded(code);
        }
        PluginContextImpl context = holder.getContext();
        return new PluginRuntimeAssets(
                code,
                true,
                holder.isEnabled(),
                context.menus().stream()
                        .map(item -> new PluginMenuAssetInfo(item.title(), item.path(), item.icon(), item.permission(), item.parentPath(), item.sort()))
                        .toList(),
                permissions(code),
                context.capabilities().stream()
                        .map(item -> new PluginCapabilityAssetInfo(item.code(), item.name(), item.type(), item.description(), item.icon(), item.dependencies()))
                        .toList(),
                context.dashboardCards().stream().map(card -> toInfo(code, card)).toList(),
                context.frontendModules().stream().map(module -> toInfo(code, module)).toList(),
                context.httpEndpoints(),
                context.commandRegistry().registrations().stream()
                        .map(registration -> new PluginCommandInfo(code, registration.definition().code(),
                                registration.definition().command(), registration.definition().name(),
                                registration.definition().permission(), registration.definition().description(),
                                registration.definition().allowAnonymous()))
                        .sorted(Comparator.comparing(PluginCommandInfo::code))
                        .toList(),
                context.interactionRegistry().registrations().stream()
                        .map(registration -> new PluginMessageInteractionInfo(code, registration.kind(),
                                registration.filter() == null ? List.of() : List.copyOf(registration.filter().eventTypes()),
                                registration.filter() == null ? null : registration.filter().platform(),
                                registration.filter() == null ? null : registration.filter().channelId(),
                                registration.filter() == null ? null : registration.filter().command()))
                        .toList(),
                aiToolRegistry.tools(code).stream()
                        .map(tool -> toAssetInfo(code, tool))
                        .toList(),
                agentApplicationRegistry.applicationsByOwner(code).stream()
                        .map(application -> new PluginRuntimeAgentInfo(code,
                                application.getId() == null ? null : String.valueOf(application.getId()),
                                application.getCode(), application.getName(), application.getDescription(),
                                application.getIcon(),
                                application.getStatus() == null ? null : application.getStatus().name()))
                        .toList(),
                pluginServiceRegistry.exportedServiceNames(code)
        );
    }

    private PluginAiToolInfo toAssetInfo(String code, PluginAiTool tool) {
        PluginAiToolDescriptor descriptor = tool.descriptor();
        return new PluginAiToolInfo(code, descriptor.name(), descriptor.title(), descriptor.description(),
                descriptor.permissionCode(), descriptor.risk() == null ? null : descriptor.risk().name(),
                descriptor.requiresConfirmation(), List.copyOf(descriptor.allowedTriggers()));
    }

    private void publishLifecycle(String code, PluginLifecycleAction action, String version, long startNanos, Throwable error) {
        try {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            eventPublisher.publishEvent(error == null
                    ? PluginLifecycleEvent.succeeded(code, action, version, durationMs)
                    : PluginLifecycleEvent.failed(code, action, version, durationMs, error.getMessage()));
        } catch (RuntimeException publishError) {
            // 事件发布失败不得影响插件生命周期主流程
            log.debug("Publish plugin lifecycle event failed: code={}, action={}", code, action, publishError);
        }
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
        // 开发模式优先从源码仓 dist 目录取前端产物，vite build --watch 的更新即时生效
        Optional<PluginFrontendAssetInfo> devAsset = devFrontendAsset(code, path);
        if (devAsset.isPresent()) {
            return devAsset;
        }
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

    private Optional<PluginFrontendAssetInfo> devFrontendAsset(String code, String path) {
        PluginDevModeProperties.DevProject project = findDevProject(code);
        if (project == null) {
            return Optional.empty();
        }
        Path dist = project.resolvedFrontendDist();
        Path file = dist.resolve(path).normalize();
        if (!file.startsWith(dist) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PluginFrontendAssetInfo(path, contentType(path), Files.readAllBytes(file)));
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
        Path pluginPath = Path.of(module.getJarPath());
        boolean directoryMode = Files.isDirectory(pluginPath);
        if (!directoryMode && !Files.isRegularFile(pluginPath)) {
            throw new BizException("插件 JAR 不存在：" + module.getJarPath());
        }
        if (directoryMode && !isDevProjectPath(module.getCode(), pluginPath)) {
            throw new BizException("仅开发模式配置的插件允许从目录加载：" + module.getCode());
        }
        try {
            PluginDescriptor descriptor = readYamlDescriptor(pluginPath);
            if (!module.getCode().equals(descriptor.code())) {
                throw new BizException("插件编码不匹配：" + descriptor.code());
            }
            URLClassLoader classLoader = directoryMode
                    ? createDevClassLoader(pluginPath, descriptor)
                    : createClassLoader(pluginPath, descriptor);
            try {
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
            } catch (RuntimeException | Error e) {
                // 实例化失败时释放 ClassLoader，避免句柄泄漏在 Windows 下锁住插件 JAR
                closeClassLoader(classLoader);
                throw e;
            }
        } catch (IOException e) {
            throw new BizException("插件 ClassLoader 创建失败：" + e.getMessage());
        }
    }

    private boolean isDevProjectPath(String code, Path pluginPath) {
        PluginDevModeProperties.DevProject project = findDevProject(code);
        return project != null
                && project.classesDir().equals(pluginPath.toAbsolutePath().normalize());
    }

    private URLClassLoader createClassLoader(Path jarPath, PluginDescriptor descriptor) throws IOException {
        URL[] urls = new URL[]{jarPath.toUri().toURL()};
        return new PluginClassLoader(urls, getClass().getClassLoader(), dependencyClassLoaders(descriptor));
    }

    /** 开发模式目录类路径：target/classes + target/plugin-dev/lib/*.jar（运行时依赖由插件仓 dev-export 导出）。 */
    private URLClassLoader createDevClassLoader(Path classesDir, PluginDescriptor descriptor) throws IOException {
        List<Path> entries = new ArrayList<>();
        entries.add(classesDir);
        PluginDevModeProperties.DevProject project = findDevProject(descriptor.code());
        Path libDir = project == null ? null : project.libDir();
        if (libDir != null && Files.isDirectory(libDir)) {
            try (Stream<Path> libs = Files.list(libDir)) {
                libs.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                        .sorted()
                        .forEach(entries::add);
            }
        }
        List<URL> urls = new ArrayList<>();
        for (Path entry : entries) {
            urls.add(entry.toUri().toURL());
        }
        return new PluginClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader(), dependencyClassLoaders(descriptor));
    }

    private PluginDescriptor readYamlDescriptor(Path pluginPath) throws IOException {
        if (Files.isDirectory(pluginPath)) {
            Path yaml = pluginPath.resolve("plugin.yml");
            if (!Files.isRegularFile(yaml)) {
                throw new BizException("插件开发目录缺少 plugin.yml：" + pluginPath);
            }
            try (InputStream inputStream = Files.newInputStream(yaml)) {
                return new PluginYamlDescriptorReader().read(inputStream);
            }
        }
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(pluginPath.toFile())) {
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
                MenuStatus.ACTIVE,
                module.styles(),
                module.scripts()
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
                route.hideInMenu(),
                route.publicAccess()
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
                card.sort(),
                card.defaultOnFirstVisit()
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
        if (lower.endsWith(".ttf")) {
            return "font/ttf";
        }
        if (lower.endsWith(".otf")) {
            return "font/otf";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".avif")) {
            return "image/avif";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".webmanifest")) {
            return "application/manifest+json";
        }
        if (lower.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
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
