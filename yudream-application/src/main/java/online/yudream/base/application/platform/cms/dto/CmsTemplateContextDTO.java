package online.yudream.base.application.platform.cms.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CmsTemplateContextDTO {
    private CmsTemplateCmsDTO cms;
    private CmsTemplateKnowledgeDTO knowledge;

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
    }
}
