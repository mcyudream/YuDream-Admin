package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HomePagePresetRes {
    private String code;
    private String name;
    private String description;
    private String source;
    private String pluginCode;
    private Integer sectionCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
