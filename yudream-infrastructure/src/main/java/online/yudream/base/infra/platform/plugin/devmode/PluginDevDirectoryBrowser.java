package online.yudream.base.infra.platform.plugin.devmode;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevDirectoryBrowseInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevDirectoryEntryInfo;
import online.yudream.base.infra.platform.plugin.service.PluginYamlDescriptorReader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 宿主机目录浏览支撑：为开发者面板的「选择目录」弹窗逐层列出子目录。
 * 安全边界：只列目录与其插件模块标记（pom.xml/plugin.yml 存在性与编码推断），
 * 不返回任何文件内容；path 为空时返回文件系统根（Windows 盘符）。
 */
@Slf4j
@Component
public class PluginDevDirectoryBrowser {

    /** 单次返回的目录条目上限，避免超大目录拖慢面板 */
    private static final int MAX_ENTRIES = 200;

    public PluginDevDirectoryBrowseInfo browse(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return new PluginDevDirectoryBrowseInfo("", null, true, false, false, null, roots());
        }
        Path dir = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new BizException("目录不存在：" + dir);
        }
        Path parent = dir.getParent();
        List<PluginDevDirectoryEntryInfo> entries;
        try (Stream<Path> stream = Files.list(dir)) {
            entries = stream
                    .filter(Files::isDirectory)
                    .filter(path -> !isHidden(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_ENTRIES)
                    .map(this::toEntry)
                    .toList();
        } catch (IOException e) {
            throw new BizException("目录读取失败：" + e.getMessage());
        }
        return new PluginDevDirectoryBrowseInfo(dir.toString(),
                parent == null ? null : parent.toString(),
                false,
                Files.isRegularFile(dir.resolve("pom.xml")),
                pluginYml(dir) != null,
                inferCode(dir),
                entries);
    }

    private List<PluginDevDirectoryEntryInfo> roots() {
        List<Path> rootPaths = new java.util.ArrayList<>();
        FileSystems.getDefault().getRootDirectories().forEach(rootPaths::add);
        return rootPaths.stream()
                .map(root -> new PluginDevDirectoryEntryInfo(root.toString(), root.toString(), false, false, null))
                .toList();
    }

    private PluginDevDirectoryEntryInfo toEntry(Path dir) {
        return new PluginDevDirectoryEntryInfo(
                dir.getFileName().toString(),
                dir.toString(),
                Files.isRegularFile(dir.resolve("pom.xml")),
                pluginYml(dir) != null,
                inferCode(dir));
    }

    /** 与 PluginDevProjectCatalog 推断编码使用同一候选顺序：编译产物优先，其次源码资源目录 */
    private Path pluginYml(Path dir) {
        List<Path> candidates = List.of(
                dir.resolve("target").resolve("classes").resolve("plugin.yml"),
                dir.resolve("src").resolve("main").resolve("resources").resolve("plugin.yml"));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private String inferCode(Path dir) {
        Path pluginYml = pluginYml(dir);
        if (pluginYml == null) {
            return null;
        }
        try (InputStream inputStream = Files.newInputStream(pluginYml)) {
            return new PluginYamlDescriptorReader().read(inputStream).code();
        } catch (Exception e) {
            log.warn("读取插件描述符失败：{}：{}", pluginYml, e.getMessage());
            return null;
        }
    }

    private boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }
}
