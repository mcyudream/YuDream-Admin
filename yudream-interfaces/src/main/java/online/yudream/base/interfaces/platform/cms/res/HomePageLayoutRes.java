package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class HomePageLayoutRes {
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
    /** 当前激活主题的公开配置（已剔除敏感字段），模板 theme.config 渲染根。 */
    private java.util.Map<String, Object> themeConfig;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
