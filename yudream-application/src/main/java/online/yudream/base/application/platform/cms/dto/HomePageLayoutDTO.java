package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class HomePageLayoutDTO {
    private Long id;
    /** 归属主题编码。 */
    private String themeCode;
    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;
    private Boolean published;
    /** 公开端按激活主题注入的主题配置（theme.config 渲染根），仅公开方法填充。 */
    private java.util.Map<String, Object> themeConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
