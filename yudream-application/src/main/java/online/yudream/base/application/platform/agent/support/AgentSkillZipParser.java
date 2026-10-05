package online.yudream.base.application.platform.agent.support;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能压缩包解析：检测 SKILL.md（根目录或单一顶层文件夹）、剥离 frontmatter、
 * 把其余文本文件按相对路径整理为技能资源；二进制文件跳过并回报，路径逃逸直接拒绝。
 */
public final class AgentSkillZipParser {

    private static final int MAX_ENTRIES = 300;
    private static final long MAX_TOTAL_BYTES = 10L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    private AgentSkillZipParser() {
    }

    public record Parsed(
            String name,
            String description,
            String content,
            Map<String, String> resources,
            List<String> skippedBinary
    ) {
    }

    public static Parsed parse(byte[] zipData, String fileName) {
        if (zipData == null || zipData.length == 0) {
            throw new BizException("压缩包内容为空");
        }
        Map<String, byte[]> files = readAll(zipData);
        String skillPath = locateSkillMd(files.keySet());
        String root = rootOf(skillPath);
        String skillMd = text(files.get(skillPath));

        Map<String, String> frontmatter = frontmatter(skillMd);
        String name = firstNonBlank(frontmatter.get("name"), folderOf(root),
                baseName(fileName), "uploaded-skill");
        String description = frontmatter.get("description");
        if (!StringUtils.hasText(description)) {
            throw new BizException("SKILL.md 缺少 frontmatter description，无法生成技能描述");
        }
        String content = body(skillMd);

        Map<String, String> resources = new LinkedHashMap<>();
        List<String> skippedBinary = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (entry.getKey().equals(skillPath)) {
                continue;
            }
            String relative = relativePath(entry.getKey(), root);
            if (relative.isBlank()) {
                continue;
            }
            if (isBinary(entry.getValue())) {
                skippedBinary.add(relative);
                continue;
            }
            resources.put(relative, text(entry.getValue()));
        }
        return new Parsed(name, description.trim(), content, Map.copyOf(resources), List.copyOf(skippedBinary));
    }

    private static Map<String, byte[]> readAll(byte[] zipData) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalize(entry.getName());
                if (path.isBlank() || path.startsWith("/") || path.contains("../") || path.contains("..\\")) {
                    // 防 zip-slip：逃逸条目直接丢弃，不影响其余文件解析
                    in.closeEntry();
                    continue;
                }
                if (files.size() >= MAX_ENTRIES) {
                    throw new BizException("压缩包文件数超过上限（" + MAX_ENTRIES + "）");
                }
                byte[] data = in.readAllBytes();
                if (data.length > MAX_FILE_BYTES) {
                    throw new BizException("压缩包内单文件超过 1MB：" + path);
                }
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new BizException("压缩包解压后总大小超过 10MB");
                }
                files.put(path, data);
                in.closeEntry();
            }
        }
        catch (IOException e) {
            throw new BizException("压缩包解析失败：" + e.getMessage());
        }
        if (files.isEmpty()) {
            throw new BizException("压缩包内没有文件");
        }
        return files;
    }

    private static String locateSkillMd(Iterable<String> paths) {
        String best = null;
        int bestDepth = Integer.MAX_VALUE;
        int ties = 0;
        for (String path : paths) {
            if (!path.equals("SKILL.md") && !path.endsWith("/SKILL.md")) {
                continue;
            }
            int depth = path.split("/").length;
            if (depth < bestDepth) {
                best = path;
                bestDepth = depth;
                ties = 1;
            }
            else if (depth == bestDepth) {
                ties++;
            }
        }
        if (best == null) {
            throw new BizException("压缩包内未找到 SKILL.md，请按技能目录规范打包（SKILL.md 置于技能根目录）");
        }
        if (ties > 1) {
            throw new BizException("压缩包含多个 SKILL.md，一次请只上传一个技能");
        }
        return best;
    }

    private static String rootOf(String skillPath) {
        int slash = skillPath.lastIndexOf('/');
        return slash < 0 ? "" : skillPath.substring(0, slash + 1);
    }

    private static String relativePath(String path, String root) {
        return root.isBlank() || !path.startsWith(root) ? path : path.substring(root.length());
    }

    /**
     * 解析 YAML frontmatter 的 name/description（行级键值，够用即可——复杂元数据由框架忽略）。
     */
    private static Map<String, String> frontmatter(String markdown) {
        Map<String, String> values = new LinkedHashMap<>();
        if (markdown == null || !markdown.stripLeading().startsWith("---")) {
            return values;
        }
        String[] lines = markdown.stripLeading().split("\r?\n");
        int end = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return values;
        }
        for (int i = 1; i < end; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            values.put(lines[i].substring(0, colon).trim(),
                    lines[i].substring(colon + 1).trim().replaceAll("^\"|\"$", ""));
        }
        return values;
    }

    private static String body(String markdown) {
        String stripped = markdown.stripLeading();
        if (!stripped.startsWith("---")) {
            return markdown == null ? "" : markdown.strip();
        }
        String[] lines = stripped.split("\r?\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) {
                return String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, lines.length)).strip();
            }
        }
        return stripped;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').trim();
    }

    private static String folderOf(String root) {
        String trimmed = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        if (!StringUtils.hasText(trimmed)) {
            return "";
        }
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }

    private static String baseName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String value = fileName.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String base = slash < 0 ? value : value.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static String text(byte[] data) {
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }
}
