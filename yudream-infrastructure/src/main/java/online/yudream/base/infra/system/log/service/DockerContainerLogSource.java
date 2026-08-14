package online.yudream.base.infra.system.log.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.log.dto.DockerLogSettingsDTO;
import online.yudream.base.application.system.log.event.DockerLogSettingsChanged;
import online.yudream.base.domain.system.log.model.SystemLogLevel;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 将外部 docker 容器的日志接入系统日志内存缓冲，与宿主日志一并实时查看、筛选与下载。
 * 配置由系统日志页面持久化到系统设置（系统日志 → 容器日志配置），环境变量 SYSTEM_LOG_DOCKER_*
 * 仅作为首次运行的默认值。支持两种传输：
 * <ul>
 *   <li>cli：调用宿主 docker CLI（docker logs --follow），适用于本地开发；</li>
 *   <li>socket：经 unix socket 直连 Docker Engine HTTP API，适用于后端运行在 Linux 容器内并挂载 /var/run/docker.sock；</li>
 *   <li>auto（默认）：socket 文件存在则用 socket，否则回退 cli。</li>
 * </ul>
 */
@Component
@Slf4j
public class DockerContainerLogSource {

    private static final Duration RESTART_DELAY = Duration.ofSeconds(5);
    private static final String SETTING_KEY = "system.log.docker";
    private static final String DEFAULT_SOCKET = "/var/run/docker.sock";

    private final SettingRepo settingRepo;
    private final ObjectMapper objectMapper;
    private final boolean enabledDefault;
    private final String containersDefault;
    private final String transportDefault;
    private final String socketDefault;
    private final long tailDefault;

    private final ExecutorService executor;
    private final List<Process> processes = new CopyOnWriteArrayList<>();
    private final Map<String, Future<?>> tasks = new ConcurrentHashMap<>();
    private volatile DockerLogSettingsDTO settings = DockerLogSettingsDTO.defaults(false, "", "auto", DEFAULT_SOCKET, 200);

    public DockerContainerLogSource(
            SettingRepo settingRepo,
            ObjectMapper objectMapper,
            @Value("${yudream.system.log.docker.enabled:false}") boolean enabledDefault,
            @Value("${yudream.system.log.docker.containers:}") String containersDefault,
            @Value("${yudream.system.log.docker.transport:auto}") String transportDefault,
            @Value("${yudream.system.log.docker.socket:/var/run/docker.sock}") String socketDefault,
            @Value("${yudream.system.log.docker.tail:200}") long tailDefault) {
        this.settingRepo = settingRepo;
        this.objectMapper = objectMapper;
        this.enabledDefault = enabledDefault;
        this.containersDefault = containersDefault;
        this.transportDefault = transportDefault;
        this.socketDefault = socketDefault;
        this.tailDefault = tailDefault;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "docker-log-source");
            thread.setDaemon(true);
            return thread;
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        reconfigure(loadSettings());
    }

    @EventListener
    public void onSettingsChanged(DockerLogSettingsChanged event) {
        reconfigure(event.settings());
    }

    @PreDestroy
    void stop() {
        stopAll();
        executor.shutdownNow();
    }

    private synchronized void reconfigure(DockerLogSettingsDTO next) {
        stopAll();
        this.settings = next;
        if (!next.enabled()) {
            log.info("Docker 容器日志采集未启用");
            return;
        }
        if (next.containers().isEmpty()) {
            log.warn("Docker 容器日志采集已启用但未配置容器清单");
            return;
        }
        for (String container : next.containers()) {
            tasks.put(container, executor.submit(() -> stream(container)));
        }
        log.info("Docker 容器日志采集已启动：containers={}, transport={}, tail={}", next.containers(), next.transport(), next.tail());
    }

    private void stopAll() {
        tasks.values().forEach(future -> future.cancel(true));
        tasks.clear();
        processes.forEach(Process::destroy);
        processes.clear();
    }

    private void stream(String container) {
        // 线程池复用线程，上一轮 cancel(true) 残留的中断状态会让本轮任务直接退出，先清除
        Thread.interrupted();
        DockerLogSettingsDTO current = this.settings;
        boolean socketTransport = useSocket(current);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (socketTransport) {
                    DockerSocketLogReader.stream(current.socket(), container, current.tail(), line -> appendLine(container, line));
                } else {
                    streamViaCli(container, current.tail());
                }
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) {
                    // future.cancel(true) 打断阻塞读取属正常取消/重配置，不记录错误
                } else if (!(error instanceof InterruptedException)) {
                    log.error("docker 日志读取失败：container={}, transport={}", container, socketTransport ? "socket" : "cli", error);
                }
            }
            sleepQuietly(RESTART_DELAY);
        }
    }

    private boolean useSocket(DockerLogSettingsDTO current) {
        return switch (current.transport()) {
            case "socket" -> true;
            case "cli" -> false;
            default -> Files.exists(Path.of(current.socket()));
        };
    }

    private void streamViaCli(String container, long tail) throws Exception {
        List<String> command = List.of("docker", "logs", "--tail", String.valueOf(tail), "--follow", "--timestamps", container);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        processes.add(process);
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLine(container, line);
            }
        } finally {
            processes.remove(process);
        }
        int exit = process.waitFor();
        log.warn("docker logs 退出：container={}, exit={}，{} 秒后重连", container, exit, RESTART_DELAY.toSeconds());
    }

    private DockerLogSettingsDTO loadSettings() {
        return settingRepo.findByKey(SETTING_KEY)
                .map(Setting::getValue)
                .filter(value -> value != null && !value.isBlank())
                .map(this::parse)
                .orElseGet(this::defaults);
    }

    private DockerLogSettingsDTO defaults() {
        return new DockerLogSettingsDTO(enabledDefault, parseContainers(containersDefault), transportDefault, socketDefault, tailDefault);
    }

    private DockerLogSettingsDTO parse(String json) {
        try {
            return objectMapper.readValue(json, DockerLogSettingsDTO.class);
        } catch (Exception error) {
            log.error("Docker 容器日志配置解析失败，回退默认值", error);
            return defaults();
        }
    }

    private void appendLine(String container, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        SystemLogBuffer.instance().append(System.currentTimeMillis(), inferLevel(line), "docker:" + container,
                "容器:" + container, "docker", null, line, null);
    }

    static SystemLogLevel inferLevel(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        if (containsAny(value, "error", "exception", "fatal", "panic", "failed")) {
            return SystemLogLevel.ERROR;
        }
        if (value.contains("warn")) {
            return SystemLogLevel.WARN;
        }
        return SystemLogLevel.INFO;
    }

    static List<String> parseContainers(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
