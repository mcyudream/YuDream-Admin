package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 主题配置分节。 */
@Data
@Builder
public class ThemeConfigSectionRes {

    private String code;
    private String title;
    private String description;
    private List<ThemeConfigFieldRes> fields;
}
