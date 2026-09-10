package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.enumerate.PageTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CmsPageDTO {
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
    private LocalDateTime publishedAt;
    /** 归属主题编码：页面按主题隔离，仅在其归属主题激活时对外可见。 */
    private String themeCode;
    private String sourcePluginCode;
    /** 公开端按激活主题注入的主题配置（theme.config 渲染根），仅公开方法填充。 */
    private java.util.Map<String, Object> themeConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
