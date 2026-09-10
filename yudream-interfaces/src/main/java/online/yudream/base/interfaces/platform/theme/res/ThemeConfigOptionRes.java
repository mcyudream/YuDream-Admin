package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

/** 主题配置下拉选项。 */
@Data
@Builder
public class ThemeConfigOptionRes {

    private String label;
    private String value;
}
