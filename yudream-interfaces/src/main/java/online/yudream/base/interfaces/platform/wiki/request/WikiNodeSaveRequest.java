package online.yudream.base.interfaces.platform.wiki.request;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import lombok.Data; import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
@Data public class WikiNodeSaveRequest { private String parentId; @NotBlank(message="节点标题不能为空") private String title; @NotBlank(message="节点路径不能为空") private String slug; @NotNull(message="节点类型不能为空") private WikiNodeType nodeType; private int sort; private String markdown; }
