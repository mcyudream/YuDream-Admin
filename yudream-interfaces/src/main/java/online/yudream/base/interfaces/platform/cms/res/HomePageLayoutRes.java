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
    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;
    private Boolean published;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
