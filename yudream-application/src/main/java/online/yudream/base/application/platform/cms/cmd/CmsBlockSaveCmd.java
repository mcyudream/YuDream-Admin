package online.yudream.base.application.platform.cms.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CmsBlockSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String description;
    private String category;
    private CmsBlockKind kind;
    private String icon;
    private String previewImageUrl;
    private String htmlContent;
    private String cssContent;
    private String jsContent;
    private String builderProjectJson;
    private List<String> tags;
    private Boolean enabled;
    private Integer sort;
}
