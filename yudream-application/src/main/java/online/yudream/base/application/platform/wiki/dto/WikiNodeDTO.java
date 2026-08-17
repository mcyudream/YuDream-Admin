package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;

import java.util.List;

@Data
@Builder
public class WikiNodeDTO {
    private String id;
    private String parentId;
    private String title;
    private String slug;
    private String path;
    private WikiNodeType nodeType;
    private int sort;
    /** 完整 Markdown（含 YAML frontmatter）。 */
    private String markdown;
    /** 正文（不含 frontmatter），供编辑器使用。 */
    private String body;
    private String publishedVersionId;
    private WikiIndexStatus indexStatus;
    private List<WikiNodeDTO> children;

    /** 页面类型（小写枚举名，如 concept），与前端展示字典一致。 */
    private String pageType;
    private List<String> sources;
    private List<String> related;
    private List<String> tags;
    private String summary;
}
