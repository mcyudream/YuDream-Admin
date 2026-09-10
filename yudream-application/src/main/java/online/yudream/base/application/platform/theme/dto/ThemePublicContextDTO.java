package online.yudream.base.application.platform.theme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.application.platform.cms.dto.CmsTemplateItemDTO;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 公开主题上下文：当前激活 SITE 主题编码、脱敏后的主题配置、
 * 按需解析的插件数据块与 CMS 最新文章，供 Vue 原生主题页消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemePublicContextDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String themeCode;
    /** 主题公开配置（secret 字段已剔除）。 */
    private Map<String, Object> themeConfig;
    /** 插件主题数据块，键为块编码；插件未启用/未适配时对应键缺省。 */
    private Map<String, Object> blocks;
    /** 当前主题下最新发布的 CMS 文章（轻量字段，无正文）。 */
    private List<CmsTemplateItemDTO> cmsPagesLatest;
}
