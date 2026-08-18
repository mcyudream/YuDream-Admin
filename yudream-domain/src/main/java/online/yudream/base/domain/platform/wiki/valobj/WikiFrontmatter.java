package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Wiki 页面的 YAML frontmatter（Obsidian 兼容）。
 * <p>
 * 页面全文 = frontmatter + 正文。本工具负责在“结构化字段”与“Markdown 全文”之间双向转换，
 * 供领域聚合与摄入生成链路使用。
 */
public record WikiFrontmatter(
        String title,
        WikiPageType pageType,
        List<String> sources,
        List<String> related,
        List<String> tags,
        String summary,
        String body
) {

    public WikiFrontmatter {
        title = title == null ? "" : title.trim();
        pageType = pageType == null ? WikiPageType.CONCEPT : pageType;
        sources = normalizeList(sources);
        related = normalizeList(related);
        tags = normalizeList(tags);
        summary = summary == null ? "" : summary.trim();
        body = body == null ? "" : body;
    }

    public static WikiFrontmatter of(String title, WikiPageType pageType, List<String> sources,
                                     List<String> related, List<String> tags, String summary, String body) {
        return new WikiFrontmatter(title, pageType, sources, related, tags, summary, body);
    }

    public static WikiFrontmatter empty(WikiPageType pageType) {
        return new WikiFrontmatter("", pageType, List.of(), List.of(), List.of(), "", "");
    }

    /**
     * 解析带 YAML frontmatter 的 Markdown 全文。缺少 frontmatter 时按普通正文处理。
     */
    public static WikiFrontmatter parse(String markdown) {
        String content = markdown == null ? "" : markdown;
        if (!content.startsWith("---")) {
            return new WikiFrontmatter("", WikiPageType.CONCEPT, List.of(), List.of(), List.of(), "", content);
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return new WikiFrontmatter("", WikiPageType.CONCEPT, List.of(), List.of(), List.of(), "", content);
        }
        String header = content.substring(3, end);
        String body = content.substring(end + 4);
        if (body.startsWith("\n")) {
            body = body.substring(1);
        }
        String title = "";
        WikiPageType pageType = WikiPageType.CONCEPT;
        List<String> sources = new ArrayList<>();
        List<String> related = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        String summary = "";
        String currentList = null;
        for (String rawLine : header.split("\r?\n")) {
            String line = rawLine.strip();
            if (line.startsWith("- ") || line.equals("-")) {
                if (currentList != null) {
                    addToList(currentList, sources, related, tags, line.startsWith("- ") ? line.substring(2).trim() : "");
                }
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                currentList = null;
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty()) {
                currentList = key;
            }
            else {
                currentList = null;
                switch (key) {
                    case "title" -> title = value;
                    case "type", "page_type" -> pageType = parseType(value);
                    case "summary", "description" -> summary = value;
                    default -> { /* 忽略未知键 */ }
                }
            }
        }
        return new WikiFrontmatter(title, pageType, sources, related, tags, summary, body);
    }

    public String fullMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("---\n");
        if (!title.isBlank()) {
            builder.append("title: ").append(title).append('\n');
        }
        builder.append("type: ").append(typeKey()).append('\n');
        if (!summary.isBlank()) {
            builder.append("summary: ").append(summary).append('\n');
        }
        if (!sources.isEmpty()) {
            builder.append("sources:\n");
            sources.forEach(item -> builder.append("  - ").append(item).append('\n'));
        }
        if (!related.isEmpty()) {
            builder.append("related:\n");
            related.forEach(item -> builder.append("  - ").append(item).append('\n'));
        }
        if (!tags.isEmpty()) {
            builder.append("tags:\n");
            tags.forEach(item -> builder.append("  - ").append(item).append('\n'));
        }
        builder.append("---\n");
        if (!body.isBlank()) {
            builder.append(body);
            if (!body.endsWith("\n")) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    public String bodyOnly() {
        return body;
    }

    private String typeKey() {
        return pageType.name().toLowerCase(Locale.ROOT);
    }

    private static WikiPageType parseType(String value) {
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return WikiPageType.valueOf(normalized);
        }
        catch (IllegalArgumentException ignored) {
            return WikiPageType.CONCEPT;
        }
    }

    private static void addToList(String listKey, List<String> sources, List<String> related, List<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (listKey) {
            case "sources" -> sources.add(value);
            case "related" -> related.add(value);
            case "tags" -> tags.add(value);
            default -> { /* 忽略未知列表 */ }
        }
    }

    private static List<String> normalizeList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank()).toList();
    }
}
