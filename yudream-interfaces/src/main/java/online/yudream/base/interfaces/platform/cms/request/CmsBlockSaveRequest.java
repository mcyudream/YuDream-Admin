package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

import java.util.List;

@Data
public class CmsBlockSaveRequest {
    @NotBlank(message = "区块编码不能为空")
    private String code;
    @NotBlank(message = "区块名称不能为空")
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
