package online.yudream.base.application.platform.wiki.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;

import java.io.Serializable;
import java.util.List;

@Data
public class WikiNodeSaveCmd implements Serializable {
    private Long id;
    private Long spaceId;
    private Long parentId;
    private String title;
    private String slug;
    private WikiNodeType nodeType;
    private int sort;

    /** 兼容旧的手工编辑入口：完整 Markdown（可含 frontmatter）。 */
    private String markdown;
    /** 新编辑器使用：正文（不含 frontmatter）。 */
    private String body;
    private WikiPageType pageType;
    private List<String> sources;
    private List<String> related;
    private List<String> tags;
    private String summary;
}
