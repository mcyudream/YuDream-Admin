package online.yudream.base.interfaces.platform.cms.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CmsTemplateContextRes {
    private CmsTemplateCmsRes cms;
    private CmsTemplateKnowledgeRes knowledge;

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
    }
}
