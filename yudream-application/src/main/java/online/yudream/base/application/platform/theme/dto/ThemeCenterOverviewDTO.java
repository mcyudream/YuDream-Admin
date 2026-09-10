package online.yudream.base.application.platform.theme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 主题中心总览：公开站主题卡列表 + 首页方案列表。
 * 内容定制能力关闭时 presets 为空且 cmsEnabled=false，主题切换不受影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCenterOverviewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ThemeCenterThemeCardDTO> themes;
    private List<HomePagePresetDTO> presets;
    /** 可在主题中心编辑内容的主题编码清单（default + 全部 SITE 插件主题 + 拥有存量布局的主题）。 */
    private List<String> editableThemes;
    private Boolean cmsEnabled;
}
