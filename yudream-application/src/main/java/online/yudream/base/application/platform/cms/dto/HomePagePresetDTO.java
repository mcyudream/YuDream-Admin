package online.yudream.base.application.platform.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页内容定制方案摘要（不含完整区块与设置内容）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePagePresetDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String description;
    /** 来源：USER / PLUGIN / SNAPSHOT。 */
    private String source;
    private String pluginCode;
    /** 方案内区块数量，供列表展示。 */
    private Integer sectionCount;
    /** 是否为当前首页正在使用的方案。 */
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
