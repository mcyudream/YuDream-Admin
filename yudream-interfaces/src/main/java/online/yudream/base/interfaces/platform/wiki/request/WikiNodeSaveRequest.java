package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;

import java.util.List;

@Data
public class WikiNodeSaveRequest {
    private String parentId;
    @NotBlank(message = "节点标题不能为空")
    private String title;
    @NotBlank(message = "节点路径不能为空")
    private String slug;
    @NotNull(message = "节点类型不能为空")
    private WikiNodeType nodeType;
    private int sort;
    /** 兼容旧编辑器：完整 Markdown（可含 frontmatter）。 */
    private String markdown;
    /** 新编辑器：正文（不含 frontmatter）。 */
    private String body;
    /** 页面类型（大小写不敏感的枚举名，如 concept/CONCEPT）。 */
    private String pageType;
    private List<String> sources;
    private List<String> related;
    private List<String> tags;
    private String summary;
}
