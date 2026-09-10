package online.yudream.base.application.platform.theme.dto;

import lombok.Data;

import java.util.List;

/** 主题配置 schema：主题经 @PluginTheme(configSchema=...) 声明的 JSON 资产解析结果。 */
@Data
public class ThemeConfigSchemaDTO {

    private List<ThemeConfigSectionDTO> sections;
}
