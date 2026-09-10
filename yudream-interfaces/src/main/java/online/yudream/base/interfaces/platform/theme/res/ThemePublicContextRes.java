package online.yudream.base.interfaces.platform.theme.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.interfaces.platform.cms.res.CmsTemplateItemRes;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 公开主题上下文响应：当前 SITE 主题、公开配置、插件数据块与 CMS 最新文章。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemePublicContextRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String themeCode;
    private Map<String, Object> themeConfig;
    private Map<String, Object> blocks;
    private List<CmsTemplateItemRes> cmsPagesLatest;
}
