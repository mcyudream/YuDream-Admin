package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CmsTemplateContextDTO {
    private CmsTemplateCmsDTO cms;
    private CmsTemplateKnowledgeDTO knowledge;
    /** 主题块数据：块 code -> 插件提供者返回的 JSON 可序列化数据，未请求或不可用的块缺省。 */
    private Map<String, Object> blocks;
    /** 插件站点贡献：启用插件注册的菜单项与插槽片段（片段已净化，可直接注入 DOM）。 */
    private CmsTemplateContributionsDTO contributions;

    @Data
    @Builder
    public static class CmsTemplateContributionsDTO {
        private List<ContributionMenuItemDTO> menu;
        /** 插槽标识（如 home.hero-bottom）→ 已净化片段 HTML。 */
        private Map<String, String> slots;
    }

    @Data
    @Builder
    public static class ContributionMenuItemDTO {
        private String pluginCode;
        private String label;
        private String path;
        private String icon;
        private int order;
    }

    @Data
    @Builder
    public static class CmsTemplateCmsDTO {
        private CmsTemplatePagesDTO pages;
    }

    @Data
    @Builder
    public static class CmsTemplatePagesDTO {
        private List<CmsTemplateItemDTO> latest;
    }

    @Data
    @Builder
    public static class CmsTemplateKnowledgeDTO {
        private List<CmsTemplateItemDTO> spaces;
        private List<CmsTemplateItemDTO> pages;
        private List<CmsTemplateItemDTO> latest;
        private List<CmsTemplateItemDTO> featured;
    }
}
