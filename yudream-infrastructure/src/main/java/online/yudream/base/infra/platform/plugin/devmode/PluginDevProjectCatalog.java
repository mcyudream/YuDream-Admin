package online.yudream.base.infra.platform.plugin.devmode;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.enumerate.PluginDevProjectSource;
import online.yudream.base.infra.platform.plugin.service.PluginDevModeProperties;
import online.yudream.base.infra.platform.plugin.service.PluginYamlDescriptorReader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 开发模式插件项目目录册：合并 yml 静态登记（CONFIG，面板只读）与开发者面板维护的
 * 本地清单文件（FILE，默认 plugins/dev-projects.json，相对 user.dir，与插件 JAR 目录同约定）。
 * 清单文件同时是 coding agent 定位插件源码目录的稳定入口。
 * 同 code 冲突时 CONFIG 优先；文件按 mtime 缓存，外部（含 agent）改动后下一轮访问自动生效。
 */
@Slf4j
@Component
public class PluginDevProjectCatalog {

    private static final long MISSING = -1;
    /** 官方插件仓模块在仓库根下两层，深度 3 覆盖再包一层的目录 */
    private static final int MAX_SCAN_DEPTH = 3;
    private static final int MAX_CANDIDATES = 100;
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", "target", "dist", "src", "build", "out", ".git", ".idea");

    private final PluginDevModeProperties properties;
    private final ObjectMapper objectMapper;

    /** 文件源项目缓存，key 为插件 code */
    private volatile Map<String, PluginDevModeProperties.DevProject> fileProjects = Map.of();
    private volatile long fileStamp = MISSING;

    public PluginDevProjectCatalog(PluginDevModeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 合并视图：yml 项目在前，文件项目剔除与 yml 同 code 的条目 */
    public List<CatalogEntry> projects() {
        Map<String, PluginDevModeProperties.DevProject> file = refreshIfChanged();
        List<CatalogEntry> merged = new ArrayList<>();
        for (PluginDevModeProperties.DevProject project : configProjects()) {
            String code = project.getCode().trim();
            merged.add(new CatalogEntry(project, PluginDevProjectSource.CONFIG));
            if (file.containsKey(code)) {
                log.warn("开发模式项目 {} 同时存在于配置文件与面板清单，以配置文件为准", code);
            }
        }
        for (PluginDevModeProperties.DevProject project : file.values()) {
            if (findConfig(project.getCode().trim()).isEmpty()) {
                merged.add(new CatalogEntry(project, PluginDevProjectSource.FILE));
            }
        }
        return merged;
    }

    public synchronized PluginDevModeProperties.DevProject add(PluginDevModeProperties.DevProject project) {
        if (project == null || !StringUtils.hasText(project.getPath())) {
            throw new BizException("插件目录不能为空");
        }
        Path root = Path.of(project.getPath().trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BizException("插件目录不存在：" + root);
        }
        project.setPath(root.toString());
        if (!StringUtils.hasText(project.getCode())) {
            project.setCode(inferCode(root).orElseThrow(() ->
                    new BizException("无法从目录推断插件编码，请先执行一次 mvn compile 或显式指定 code：" + root)));
        }
        project.setCode(project.getCode().trim());
        if (findConfig(project.getCode()).isPresent()) {
            throw new BizException("插件 " + project.getCode() + " 已在配置文件中登记，无需重复添加");
        }
        Map<String, PluginDevModeProperties.DevProject> current = new LinkedHashMap<>(refreshIfChanged());
        current.put(project.getCode(), project);
        persist(current);
        log.warn("插件开发模式项目已由面板登记：{} -> {}", project.getCode(), project.getPath());
        return project;
    }

    /**
     * 从父目录有界扫描插件模块并批量写入 FILE 清单，只 persist 一次。
     * 已在 CONFIG/FILE 登记、扫描内编码冲突、同路径重复的条目列入 skipped。
     */
    public synchronized CatalogBatchResult addFromDirectory(Path parent) {
        if (parent == null) {
            throw new BizException("插件目录不能为空");
        }
        Path root = parent.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BizException("插件目录不存在：" + root);
        }
        List<Path> candidates = new ArrayList<>();
        collectPluginDirs(root, 0, candidates);
        candidates.sort(Comparator.comparing(Path::toString));

        Map<String, PluginDevModeProperties.DevProject> file = new LinkedHashMap<>(refreshIfChanged());
        Set<String> existingPaths = new HashSet<>();
        for (PluginDevModeProperties.DevProject project : configProjects()) {
            existingPaths.add(normalizePath(project.getPath()));
        }
        for (PluginDevModeProperties.DevProject project : file.values()) {
            existingPaths.add(normalizePath(project.getPath()));
        }

        List<PluginDevModeProperties.DevProject> registered = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        Set<String> scanCodes = new HashSet<>();
        for (Path candidate : candidates) {
            String pathStr = candidate.toString();
            Optional<String> inferred = inferCode(candidate);
            if (inferred.isEmpty()) {
                skipped.add(new Skipped(null, pathStr, "无法推断编码"));
                continue;
            }
            String code = inferred.get();
            if (!scanCodes.add(code)) {
                skipped.add(new Skipped(code, pathStr, "编码冲突"));
                continue;
            }
            if (findConfig(code).isPresent()) {
                skipped.add(new Skipped(code, pathStr, "已在配置文件登记"));
                continue;
            }
            if (file.containsKey(code)) {
                skipped.add(new Skipped(code, pathStr, "已登记"));
                continue;
            }
            if (!existingPaths.add(pathStr)) {
                skipped.add(new Skipped(code, pathStr, "目录已登记"));
                continue;
            }
            PluginDevModeProperties.DevProject project = new PluginDevModeProperties.DevProject();
            project.setCode(code);
            project.setPath(pathStr);
            project.setAutoCompile(true);
            project.setCompileCommand(new PluginDevModeProperties.DevProject().getCompileCommand());
            file.put(code, project);
            registered.add(project);
        }
        if (!registered.isEmpty()) {
            persist(file);
            for (PluginDevModeProperties.DevProject project : registered) {
                log.warn("插件开发模式项目已由面板登记：{} -> {}", project.getCode(), project.getPath());
            }
        }
        return new CatalogBatchResult(List.copyOf(registered), List.copyOf(skipped));
    }

    public synchronized void remove(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException("插件编码不能为空");
        }
        String trimmed = code.trim();
        if (findConfig(trimmed).isPresent()) {
            throw new BizException("插件 " + trimmed + " 来自配置文件，请在 yml 中移除");
        }
        Map<String, PluginDevModeProperties.DevProject> current = new LinkedHashMap<>(refreshIfChanged());
        if (current.remove(trimmed) == null) {
            throw new BizException("开发模式项目不存在：" + trimmed);
        }
        persist(current);
        log.warn("插件开发模式项目已由面板移除：{}", trimmed);
    }

    public boolean configSourced(String code) {
        return StringUtils.hasText(code) && findConfig(code.trim()).isPresent();
    }

    public Path storeFile() {
        return properties.resolvedStoreFile();
    }

    private List<PluginDevModeProperties.DevProject> configProjects() {
        return properties.getProjects() == null ? List.of() : properties.getProjects().stream()
                .filter(project -> project != null
                        && StringUtils.hasText(project.getCode())
                        && StringUtils.hasText(project.getPath()))
                .toList();
    }

    private Optional<PluginDevModeProperties.DevProject> findConfig(String code) {
        return configProjects().stream()
                .filter(project -> code.equals(project.getCode().trim()))
                .findFirst();
    }

    private void collectPluginDirs(Path dir, int depth, List<Path> candidates) {
        if (candidates.size() >= MAX_CANDIDATES) {
            return;
        }
        if (isPluginModule(dir)) {
            candidates.add(dir.toAbsolutePath().normalize());
            return;
        }
        if (depth >= MAX_SCAN_DEPTH) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> children = stream.filter(Files::isDirectory)
                    .sorted()
                    .toList();
            for (Path child : children) {
                if (candidates.size() >= MAX_CANDIDATES) {
                    return;
                }
                String name = child.getFileName().toString();
                if (name.startsWith(".") || SKIP_DIR_NAMES.contains(name)) {
                    continue;
                }
                collectPluginDirs(child, depth + 1, candidates);
            }
        } catch (IOException e) {
            log.warn("扫描开发项目目录失败：{}：{}", dir, e.getMessage());
        }
    }

    private boolean isPluginModule(Path dir) {
        return Files.isRegularFile(dir.resolve("target").resolve("classes").resolve("plugin.yml"))
                || Files.isRegularFile(dir.resolve("src").resolve("main").resolve("resources").resolve("plugin.yml"));
    }

    private String normalizePath(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    /** 依次尝试编译产物与源码资源目录中的 plugin.yml 推断插件编码 */
    private Optional<String> inferCode(Path root) {
        List<Path> candidates = List.of(
                root.resolve("target").resolve("classes").resolve("plugin.yml"),
                root.resolve("src").resolve("main").resolve("resources").resolve("plugin.yml"));
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (InputStream inputStream = Files.newInputStream(candidate)) {
                return Optional.of(new PluginYamlDescriptorReader().read(inputStream).code());
            } catch (Exception e) {
                log.warn("读取插件描述符失败：{}：{}", candidate, e.getMessage());
            }
        }
        return Optional.empty();
    }

    private Map<String, PluginDevModeProperties.DevProject> refreshIfChanged() {
        long stamp = stamp(storeFile());
        if (stamp == fileStamp) {
            return fileProjects;
        }
        synchronized (this) {
            if (stamp == fileStamp) {
                return fileProjects;
            }
            Map<String, PluginDevModeProperties.DevProject> loaded = load(storeFile());
            fileProjects = loaded;
            fileStamp = stamp;
            return loaded;
        }
    }

    private Map<String, PluginDevModeProperties.DevProject> load(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            DevProjectStore store = objectMapper.readValue(file.toFile(), DevProjectStore.class);
            Map<String, PluginDevModeProperties.DevProject> result = new LinkedHashMap<>();
            if (store.projects != null) {
                for (PluginDevModeProperties.DevProject project : store.projects) {
                    if (project != null && StringUtils.hasText(project.getCode()) && StringUtils.hasText(project.getPath())) {
                        result.put(project.getCode().trim(), project);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("开发模式项目清单读取失败，按空清单处理：{}：{}", file, e.getMessage());
            return Map.of();
        }
    }

    private void persist(Map<String, PluginDevModeProperties.DevProject> projects) {
        Path file = storeFile();
        try {
            Files.createDirectories(file.getParent());
            DevProjectStore store = new DevProjectStore();
            store.version = 1;
            store.projects = List.copyOf(projects.values());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), store);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            fileProjects = Map.copyOf(projects);
            fileStamp = stamp(file);
        } catch (IOException e) {
            throw new BizException("开发模式项目清单写入失败：" + e.getMessage());
        }
    }

    private long stamp(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : MISSING;
        } catch (IOException e) {
            return MISSING;
        }
    }

    /** 目录册条目：项目配置 + 登记来源 */
    public record CatalogEntry(PluginDevModeProperties.DevProject project, PluginDevProjectSource source) {
    }

    /** 批量扫描结果：新写入 FILE 的项目与跳过原因 */
    public record CatalogBatchResult(List<PluginDevModeProperties.DevProject> registered, List<Skipped> skipped) {
    }

    public record Skipped(String code, String path, String reason) {
    }

    /** 清单文件结构：{"version":1,"projects":[...]} */
    @SuppressWarnings("unused")
    private static final class DevProjectStore {
        public int version;
        public List<PluginDevModeProperties.DevProject> projects;
    }
}
