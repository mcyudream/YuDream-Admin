package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 主题配置 schema。 */
@Data
@Builder
public class ThemeConfigSchemaRes {

    private List<ThemeConfigSectionRes> sections;
}
