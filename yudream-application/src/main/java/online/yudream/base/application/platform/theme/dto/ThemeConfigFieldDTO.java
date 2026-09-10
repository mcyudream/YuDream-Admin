package online.yudream.base.application.platform.theme.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

/**
 * 主题配置字段定义（主题 JAR 内 theme-config.json 的字段节点）。
 * type 取值：text/textarea/number/switch/select/color/image/list；
 * list 类型通过 itemFields 声明子字段，其余类型 itemFields 为空。
 */
@Data
public class ThemeConfigFieldDTO {

    /** 字段键，模板中以 theme.config.{key} 引用；需匹配 [a-zA-Z][a-zA-Z0-9_-]*。 */
    private String key;
    private String label;
    private String description;
    private String type;
    private String placeholder;
    /** 默认值，类型随 type 而定（switch 为布尔、number 为数值、list 为数组）。 */
    @JsonAlias("default")
    private Object defaultValue;
    /** select 类型的可选项。 */
    private List<ThemeConfigOptionDTO> options;
    /** 敏感字段：加密存储、管理端脱敏、公开输出剔除；仅支持顶层字段。 */
    private Boolean secret;
    /** list 类型的子字段定义。 */
    private List<ThemeConfigFieldDTO> itemFields;
}
