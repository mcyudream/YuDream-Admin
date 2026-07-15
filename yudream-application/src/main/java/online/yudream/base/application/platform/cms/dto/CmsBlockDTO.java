package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CmsBlockDTO {
    private Long id;
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
