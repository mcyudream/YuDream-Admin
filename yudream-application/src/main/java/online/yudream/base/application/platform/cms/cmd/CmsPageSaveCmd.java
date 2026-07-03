package online.yudream.base.application.platform.cms.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CmsPageSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String markdownContent;
    private String seoTitle;
    private String seoDescription;
    private PageStatus status;
}
