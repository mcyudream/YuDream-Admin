package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CmsPageRes {
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
    private String jsContent;
    private String builderProjectJson;
    private String seoTitle;
    private String seoDescription;
    private PageTemplate template;
    private PageStatus status;
    /** 当前激活主题的公开配置（已剔除敏感字段），模板 theme.config 渲染根。 */
    private java.util.Map<String, Object> themeConfig;
    private LocalDateTime publishedAt;
    /** 归属主题编码：页面按主题隔离，仅在其归属主题激活时对外可见。 */
    private String themeCode;
    private String sourcePluginCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
