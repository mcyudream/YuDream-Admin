package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki 目录/页面节点。
 * <p>
 * 页面采用 llm_wiki 的文件化心智模型：全文 Markdown 自带 YAML frontmatter（title/type/sources/
 * related/tags/summary），同时把结构化 frontmatter 字段冗余在聚合上以便查询与索引。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiNode extends BaseDomain {

    private Long spaceId;
    private Long parentId;
    private String ancestorPath;
    private String title;
    private String slug;
    private WikiNodeType nodeType;
    private int sort;
    private String markdownDraft;
    private Long publishedVersionId;

    private WikiPageType pageType;
    private List<String> sources;
    private List<String> related;
    private List<String> tags;
    private String summary;

    public static WikiNode directory(Long spaceId, Long parentId, String title, String slug, int sort) {
        return create(spaceId, parentId, title, slug, WikiNodeType.DIRECTORY, sort);
    }

    public static WikiNode page(Long spaceId, Long parentId, String title, String slug, int sort) {
        return create(spaceId, parentId, title, slug, WikiNodeType.PAGE, sort);
    }

    private static WikiNode create(Long spaceId, Long parentId, String title, String slug, WikiNodeType type, int sort) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (blank(title)) {
            throw new BizException("节点标题不能为空");
        }
        if (blank(slug)) {
            throw new BizException("节点路径不能为空");
        }
        return WikiNode.builder().spaceId(spaceId).parentId(parentId).ancestorPath("/").title(title.trim())
                .slug(slug.trim()).nodeType(type).sort(Math.max(sort, 0)).markdownDraft("")
                .pageType(WikiPageType.CONCEPT).sources(new ArrayList<>()).related(new ArrayList<>())
                .tags(new ArrayList<>()).summary("").build();
    }

    public void ensureCanContain(WikiNodeType childType) {
        if (nodeType != WikiNodeType.DIRECTORY) {
            throw new BizException("页面节点不能包含子节点");
        }
        if (childType == null) {
            throw new BizException("节点类型不能为空");
        }
    }

    public void moveTo(Long targetParentId, String targetParentPath) {
        String ownId = getId() == null ? "" : getId().toString();
        if (targetParentId != null && targetParentId.equals(getId())) {
            throw new BizException("节点不能移动到自身");
        }
        String normalizedPath = targetParentPath == null ? "/" : targetParentPath.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }
        if (!ownId.isEmpty() && normalizedPath.contains("/" + ownId + "/")) {
            throw new BizException("节点不能移动到自己的后代目录");
        }
        this.parentId = targetParentId;
        this.ancestorPath = normalizedPath;
    }

    /**
     * 保存页面（结构化字段 + 正文），渲染为带 frontmatter 的完整 Markdown。
     */
    public void savePage(String title, WikiPageType pageType, List<String> sources, List<String> related,
                         List<String> tags, String summary, String body) {
        if (nodeType != WikiNodeType.PAGE) {
            throw new BizException("目录节点不能保存 Markdown 内容");
        }
        if (blank(title)) {
            throw new BizException("页面标题不能为空");
        }
        WikiFrontmatter frontmatter = WikiFrontmatter.of(title, pageType, sources, related, tags, summary, body);
        applyFrontmatter(frontmatter);
        this.markdownDraft = frontmatter.fullMarkdown();
    }

    /**
     * 摄入/生成链路写入完整 Markdown（必须带 YAML frontmatter），聚合自动拆出结构化字段。
     */
    public void applyGeneratedMarkdown(String fullMarkdown) {
        if (nodeType != WikiNodeType.PAGE) {
            throw new BizException("目录节点不能保存 Markdown 内容");
        }
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(fullMarkdown);
        if (frontmatter.title().isBlank()) {
            throw new BizException("生成的 Wiki 页面缺少标题");
        }
        this.markdownDraft = fullMarkdown == null ? "" : fullMarkdown;
        applyFrontmatter(frontmatter);
    }

    /**
     * 兼容既有手工编辑入口：无 frontmatter 时按当前结构化字段补全，否则按全文解析。
     */
    public void saveDraft(String title, String markdown) {
        if (nodeType != WikiNodeType.PAGE) {
            throw new BizException("目录节点不能保存 Markdown 内容");
        }
        if (blank(title)) {
            throw new BizException("页面标题不能为空");
        }
        String content = markdown == null ? "" : markdown;
        if (content.stripLeading().startsWith("---")) {
            applyGeneratedMarkdown(content);
        }
        else {
            savePage(title, pageType == null ? WikiPageType.CONCEPT : pageType, sources, related, tags, summary, content);
        }
    }

    public String bodyMarkdown() {
        return WikiFrontmatter.parse(markdownDraft == null ? "" : markdownDraft).bodyOnly();
    }

    public WikiFrontmatter frontmatter() {
        return WikiFrontmatter.parse(markdownDraft == null ? "" : markdownDraft);
    }

    private void applyFrontmatter(WikiFrontmatter frontmatter) {
        this.title = frontmatter.title();
        this.pageType = frontmatter.pageType();
        this.sources = new ArrayList<>(frontmatter.sources());
        this.related = new ArrayList<>(frontmatter.related());
        this.tags = new ArrayList<>(frontmatter.tags());
        this.summary = frontmatter.summary();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
