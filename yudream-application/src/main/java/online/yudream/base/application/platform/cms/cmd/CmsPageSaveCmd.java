package online.yudream.base.application.platform.cms.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CmsPageSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String excerpt;
    private String coverImageUrl;
    private List<String> categories;
    private List<String> tags;
    private String markdownContent;
    private String htmlContent;
    private String cssContent;
    private String builderProjectJson;
    private String seoTitle;
    private String seoDescription;
    private PageTemplate template;
    private PageStatus status;
}
