package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendRouteInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.frontend.PluginFrontendModule;
import online.yudream.base.plugin.spi.frontend.PluginFrontendRoute;
import online.yudream.base.plugin.spi.http.PluginHttpHandler;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.permission.PluginPermissionItem;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JarPluginRuntimeGateway implements PluginRuntimeGateway {

    private final PluginProperties pluginProperties;
    private final FrameworkServices frameworkServices;
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
        holder.getPlugin().onDisable(holder.getContext());
        holder.getContext().clearRuntimeContributions();
        holder.setEnabled(false);
        log.info("Plugin disabled: code={}", code);
    }

    @Override
    public void unload(String code) {
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
        PluginHttpResponse response = handler.handle(pluginRequest);
        return new PluginHttpDispatchResult(response.status(), response.headers(), response.contentType(), response.body(), response.wrapped());
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
        try (URLClassLoader classLoader = createClassLoader(jarPath)) {
            YuDreamPlugin plugin = loadPlugin(classLoader)
                    .orElseThrow(() -> new BizException("JAR 未声明 YuDreamPlugin 服务"));
            return Optional.of(toInfo(plugin.descriptor(), jarPath));
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
            URLClassLoader classLoader = createClassLoader(jarPath);
            YuDreamPlugin plugin = loadPlugin(classLoader)
                    .orElseThrow(() -> new BizException("JAR 未声明 YuDreamPlugin 服务"));
            PluginDescriptor descriptor = plugin.descriptor();
            if (!module.getCode().equals(descriptor.code())) {
                closeClassLoader(classLoader);
                throw new BizException("插件编码不匹配：" + descriptor.code());
            }
            return new PluginRuntimeHolder(
                    classLoader,
                    plugin,
                    descriptor,
                    new PluginContextImpl(module.getCode(), frameworkServices)
            );
        } catch (IOException e) {
            throw new BizException("插件 ClassLoader 创建失败：" + e.getMessage());
        }
    }

    private Optional<YuDreamPlugin> loadPlugin(URLClassLoader classLoader) {
        ServiceLoader<YuDreamPlugin> loader = ServiceLoader.load(YuDreamPlugin.class, classLoader);
        return loader.findFirst();
    }

    private URLClassLoader createClassLoader(Path jarPath) throws IOException {
        URL[] urls = new URL[]{jarPath.toUri().toURL()};
        return new URLClassLoader(urls, getClass().getClassLoader());
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
                descriptor.dependencies()
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
                module.routes().stream().map(this::toInfo).toList()
        );
    }

    private PluginFrontendRouteInfo toInfo(PluginFrontendRoute route) {
        return new PluginFrontendRouteInfo(
                route.path(),
                route.name(),
                route.title(),
                route.icon(),
                route.component(),
                route.permission(),
                route.sort()
        );
    }

    private void closeClassLoader(URLClassLoader classLoader) {
        try {
            classLoader.close();
        } catch (IOException e) {
            log.warn("Failed to close plugin classloader", e);
        }
    }
}
