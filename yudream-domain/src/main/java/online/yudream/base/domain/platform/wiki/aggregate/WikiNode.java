package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;

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

    public static WikiNode directory(Long spaceId, Long parentId, String title, String slug, int sort) {
        return create(spaceId, parentId, title, slug, WikiNodeType.DIRECTORY, sort);
    }

    public static WikiNode page(Long spaceId, Long parentId, String title, String slug, int sort) {
        return create(spaceId, parentId, title, slug, WikiNodeType.PAGE, sort);
    }

    private static WikiNode create(Long spaceId, Long parentId, String title, String slug, WikiNodeType type, int sort) {
        if (spaceId == null) throw new BizException("知识库不能为空");
        if (blank(title)) throw new BizException("节点标题不能为空");
        if (blank(slug)) throw new BizException("节点路径不能为空");
        return WikiNode.builder().spaceId(spaceId).parentId(parentId).ancestorPath("/").title(title.trim())
                .slug(slug.trim()).nodeType(type).sort(Math.max(sort, 0)).build();
    }

    public void ensureCanContain(WikiNodeType childType) {
        if (nodeType != WikiNodeType.DIRECTORY) throw new BizException("页面节点不能包含子节点");
        if (childType == null) throw new BizException("节点类型不能为空");
    }

    public void moveTo(Long targetParentId, String targetParentPath) {
        String ownId = getId() == null ? "" : getId().toString();
        if (targetParentId != null && targetParentId.equals(getId())) throw new BizException("节点不能移动到自身");
        String normalizedPath = targetParentPath == null ? "/" : targetParentPath;
        if (!ownId.isEmpty() && normalizedPath.contains("/" + ownId + "/")) {
            throw new BizException("节点不能移动到自己的后代目录");
        }
        this.parentId = targetParentId;
        this.ancestorPath = normalizedPath.endsWith("/") ? normalizedPath : normalizedPath + "/";
    }

    public void saveDraft(String title, String markdown) {
        if (nodeType != WikiNodeType.PAGE) throw new BizException("目录节点不能保存 Markdown 内容");
        if (blank(title)) throw new BizException("页面标题不能为空");
        this.title = title.trim();
        this.markdownDraft = markdown == null ? "" : markdown;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
