package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiNode")
public class WikiNodeDO extends BaseDO {
    @Indexed
    private Long spaceId;
    @Indexed
    private Long parentId;
    private String ancestorPath;
    private String title;
    private String slug;
    private WikiNodeType nodeType;
    private int sort;
    private String markdownDraft;
    private Long publishedVersionId;

    private WikiPageType pageType;
    private List<String> sources = new ArrayList<>();
    private List<String> related = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private String summary;
}
