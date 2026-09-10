package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CmsTemplateContextRes {
    private CmsTemplateCmsRes cms;
    private CmsTemplateKnowledgeRes knowledge;
    /** 主题块数据：块 code -> 插件提供者返回的公开数据，未请求或不可用的块缺省。 */
    private Map<String, Object> blocks;

    @Data
    @Builder
    public static class CmsTemplateCmsRes {
        private CmsTemplatePagesRes pages;
    }

    @Data
    @Builder
    public static class CmsTemplatePagesRes {
        private List<CmsTemplateItemRes> latest;
    }

    @Data
    @Builder
    public static class CmsTemplateKnowledgeRes {
        private List<CmsTemplateItemRes> spaces;
        private List<CmsTemplateItemRes> pages;
        private List<CmsTemplateItemRes> latest;
        private List<CmsTemplateItemRes> featured;
    }
}
