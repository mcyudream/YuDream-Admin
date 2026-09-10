package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 主题配置字段定义。type 取值：text/textarea/number/switch/select/color/image/list；
 * list 类型经 itemFields 声明子字段。
 */
@Data
@Builder
public class ThemeConfigFieldRes {

    private String key;
    private String label;
    private String description;
    private String type;
    private String placeholder;
    private Object defaultValue;
    private List<ThemeConfigOptionRes> options;
    private Boolean secret;
    private List<ThemeConfigFieldRes> itemFields;
}
