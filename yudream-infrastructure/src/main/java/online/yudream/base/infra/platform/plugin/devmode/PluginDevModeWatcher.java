package online.yudream.base.infra.platform.plugin.devmode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.enumerate.PluginDevProjectSource;
import online.yudream.base.domain.platform.plugin.event.PluginLifecycleEvent;
import online.yudream.base.infra.platform.plugin.service.PluginDevModeProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * 插件开发模式监听器：轮询插件源码仓的 src/main/java、target/classes 与前端 dist 产物，
 * Java 变化按需触发编译，类产物变化触发宿主热重载，前端产物变化通知调试抽屉重挂载远程模块。
 * 仅限本地开发；enabled 未显式配置时按宿主运行形态自动判定（源码运行自动开启），
 * 因此不能用 ConditionalOnProperty 硬门控，改在启动时按生效值决定是否起监听线程。
 */
@Slf4j
@Component
public class PluginDevModeWatcher {

    private static final long MISSING = -1;

    private final PluginDevModeProperties properties;
    private final PluginDevProjectCatalog catalog;
    private final DevModeEnvironment environment;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, WatchState> states = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public PluginDevModeWatcher(PluginDevModeProperties properties, PluginDevProjectCatalog catalog,
                                DevModeEnvironment environment, ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.catalog = catalog;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    void start() {
        if (!properties.effectiveEnabled(environment)) {
            return;
        }
        List<PluginDevProjectCatalog.CatalogEntry> projects = catalog.projects();
        String gate = properties.autoDetected() ? "自动检测：源码运行" : "配置开启";
        log.warn("插件开发模式已启用（{}）：监听 {} 个源码项目并自动热重载，请勿在生产环境开启", gate, projects.size());
        for (PluginDevProjectCatalog.CatalogEntry entry : projects) {
            log.warn("插件开发模式项目：{} -> {}（{}）", entry.project().getCode(), entry.project().getPath(),
                    entry.source() == PluginDevProjectSource.FILE ? "面板登记" : "配置文件");
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("plugin-dev-watcher", 0).factory());
        long interval = Math.max(500, properties.getPollIntervalMs());
        executor.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception e) {
            log.warn("Plugin dev-mode watch poll failed", e);
        }
    }

    private void poll() {
        long now = System.currentTimeMillis();
        List<PluginDevModeProperties.DevProject> projects = catalog.projects().stream()
                .map(PluginDevProjectCatalog.CatalogEntry::project)
                .toList();
        // 目录册中已移除的项目不再监听，避免陈旧状态滞留
        states.keySet().retainAll(projects.stream()
                .map(PluginDevModeProperties.DevProject::getCode)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet()));
        for (PluginDevModeProperties.DevProject project : projects) {
            if (project == null || !StringUtils.hasText(project.getCode()) || !StringUtils.hasText(project.getPath())) {
                continue;
            }
            String code = project.getCode().trim();
            WatchState state = states.computeIfAbsent(code, ignored -> new WatchState());
            long[] javaStamp = treeStamp(project.sourceDir());
            long[] classesStamp = treeStamp(project.classesDir());
            long[] distStamp = treeStamp(project.resolvedFrontendDist());
            if (!state.initialized) {
                state.initialize(javaStamp, classesStamp, distStamp);
                continue;
            }
            if (changed(javaStamp, state.javaStamp)) {
                state.javaStamp = javaStamp;
                if (project.isAutoCompile()) {
                    state.compileDueAt = now + properties.getDebounceMs();
                }
            }
            if (changed(classesStamp, state.classesStamp)) {
                state.classesStamp = classesStamp;
                state.reloadDueAt = now + properties.getDebounceMs();
            }
            if (changed(distStamp, state.distStamp)) {
                state.distStamp = distStamp;
                state.frontendDueAt = now + properties.getDebounceMs();
            }
            if (state.compileDueAt > 0 && now >= state.compileDueAt && !state.compiling) {
                state.compileDueAt = 0;
                compile(project, state);
            }
            // 编译待执行/进行中时不重载陈旧产物；编译完成后类产物变化会再次推动重载
            if (state.reloadDueAt > 0 && now >= state.reloadDueAt && !state.compiling && state.compileDueAt == 0) {
                state.reloadDueAt = 0;
                log.info("Dev-mode classes changed, requesting plugin reload: {}", code);
                eventPublisher.publishEvent(PluginDevReloadRequested.of(code));
            }
            if (state.frontendDueAt > 0 && now >= state.frontendDueAt) {
                state.frontendDueAt = 0;
                eventPublisher.publishEvent(PluginLifecycleEvent.succeeded(
                        code, PluginLifecycleAction.FRONTEND_RELOAD, null, null));
            }
        }
    }

    private void compile(PluginDevModeProperties.DevProject project, WatchState state) {
        state.compiling = true;
        long startNanos = System.nanoTime();
        try {
            ProcessBuilder builder = new ProcessBuilder(shellWrap(project.getCompileCommand()));
            builder.directory(Path.of(project.getPath()).toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            AtomicReference<String> output = new AtomicReference<>("");
            Thread reader = Thread.ofVirtual().start(() -> output.set(readQuietly(process.getInputStream())));
            boolean finished = process.waitFor(properties.getCompileTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                publishCompile(project, startNanos, "编译超时（" + properties.getCompileTimeoutSeconds() + "s）");
                state.reloadDueAt = 0;
                return;
            }
            reader.join(2_000);
            if (process.exitValue() == 0) {
                publishCompile(project, startNanos, null);
            } else {
                // 编译失败不重载陈旧产物，等待下一次修改
                state.reloadDueAt = 0;
                publishCompile(project, startNanos,
                        "编译失败（exit=" + process.exitValue() + "）：" + tail(output.get(), 2_000));
            }
        } catch (Exception e) {
            state.reloadDueAt = 0;
            publishCompile(project, startNanos, rootMessage(e));
        } finally {
            state.compiling = false;
        }
    }

    private void publishCompile(PluginDevModeProperties.DevProject project, long startNanos, String error) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        PluginLifecycleEvent event = error == null
                ? PluginLifecycleEvent.succeeded(project.getCode(), PluginLifecycleAction.COMPILE, null, durationMs)
                : PluginLifecycleEvent.failed(project.getCode(), PluginLifecycleAction.COMPILE, null, durationMs, error);
        eventPublisher.publishEvent(event);
        if (error != null) {
            log.warn("Dev-mode compile failed for {}: {}", project.getCode(), error);
        }
    }

    private List<String> shellWrap(String command) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of("cmd", "/c", command);
        }
        return List.of("sh", "-c", command);
    }

    private String readQuietly(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String tail(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(trimmed.length() - maxLength);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "编译触发失败" : cursor.getMessage();
    }

    /** 返回 {最大修改时间, 文件数}，目录不存在时均为 MISSING。 */
    private long[] treeStamp(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return new long[]{MISSING, MISSING};
        }
        try (Stream<Path> paths = Files.walk(root)) {
            long[] stamp = paths.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .collect(() -> new long[]{MISSING, 0},
                            (acc, mtime) -> {
                                acc[0] = Math.max(acc[0], mtime);
                                acc[1] += 1;
                            },
                            (left, right) -> {
                                left[0] = Math.max(left[0], right[0]);
                                left[1] += right[1];
                            });
            return stamp;
        } catch (IOException e) {
            return new long[]{MISSING, MISSING};
        }
    }

    private boolean changed(long[] current, long[] previous) {
        return current[0] != previous[0] || current[1] != previous[1];
    }

    private static final class WatchState {
        private boolean initialized;
        private long[] javaStamp = new long[]{MISSING, MISSING};
        private long[] classesStamp = new long[]{MISSING, MISSING};
        private long[] distStamp = new long[]{MISSING, MISSING};
        private long compileDueAt;
        private long reloadDueAt;
        private long frontendDueAt;
        private volatile boolean compiling;

        private void initialize(long[] javaStamp, long[] classesStamp, long[] distStamp) {
            this.javaStamp = javaStamp;
            this.classesStamp = classesStamp;
            this.distStamp = distStamp;
            this.initialized = true;
        }
    }
}
