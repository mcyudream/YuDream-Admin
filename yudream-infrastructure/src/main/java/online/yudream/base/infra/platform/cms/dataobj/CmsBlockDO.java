package online.yudream.base.infra.platform.cms.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformCmsBlock")
public class CmsBlockDO extends BaseDO {
    @Indexed(unique = true)
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
    private Boolean builtin;
    private Integer sort;
}
