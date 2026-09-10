package online.yudream.base.application.platform.theme.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 主题配置管理端视图：schema + 合并默认值后的当前值。
 * 敏感字段的值恒为空串，是否已配置见 secretConfigured。
 */
@Data
@Builder
public class ThemeConfigDTO {

    private String themeCode;
    private ThemeConfigSchemaDTO schema;
    private Map<String, Object> values;
    /** 敏感字段配置状态：key -> 是否已保存过密文。 */
    private Map<String, Boolean> secretConfigured;
}
