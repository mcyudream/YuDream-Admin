package online.yudream.base.application.platform.wiki.cmd;
import lombok.Data; import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType; import java.io.Serializable;
@Data public class WikiNodeSaveCmd implements Serializable { private Long id; private Long spaceId; private Long parentId; private String title; private String slug; private WikiNodeType nodeType; private int sort; private String markdown; }
