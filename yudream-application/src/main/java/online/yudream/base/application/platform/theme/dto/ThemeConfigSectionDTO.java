package online.yudream.base.application.platform.theme.dto;

import lombok.Data;

import java.util.List;

/** 主题配置分节：配置大页面左侧锚点导航与右侧表单卡片的分组单元。 */
@Data
public class ThemeConfigSectionDTO {

    private String code;
    private String title;
    private String description;
    private List<ThemeConfigFieldDTO> fields;
}
