package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 主题配置管理端视图：schema + 合并默认值后的当前值。
 * 敏感字段的值恒为空串，是否已配置见 secretConfigured。
 */
@Data
@Builder
public class ThemeConfigRes {

    private String themeCode;
    /** 主题未声明配置 schema 时为 null。 */
    private ThemeConfigSchemaRes schema;
    private Map<String, Object> values;
    private Map<String, Boolean> secretConfigured;
}
