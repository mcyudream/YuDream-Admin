package online.yudream.base.interfaces.platform.cms.assembler;

import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.dto.CmsTemplateItemDTO;
import online.yudream.base.interfaces.platform.cms.res.CmsTemplateContextRes;
import online.yudream.base.interfaces.platform.cms.res.CmsTemplateItemRes;

public final class CmsTemplateContextWebAssembler {

    private CmsTemplateContextWebAssembler() {
    }

    public static CmsTemplateContextRes toRes(CmsTemplateContextDTO dto) {
        return CmsTemplateContextRes.builder()
                .cms(CmsTemplateContextRes.CmsTemplateCmsRes.builder()
                        .pages(CmsTemplateContextRes.CmsTemplatePagesRes.builder()
                                .latest(dto.getCms().getPages().getLatest().stream().map(CmsTemplateContextWebAssembler::toRes).toList())
                                .build())
                        .build())
                .knowledge(CmsTemplateContextRes.CmsTemplateKnowledgeRes.builder()
                        .spaces(dto.getKnowledge().getSpaces().stream().map(CmsTemplateContextWebAssembler::toRes).toList())
                        .pages(dto.getKnowledge().getPages().stream().map(CmsTemplateContextWebAssembler::toRes).toList())
                        .latest(dto.getKnowledge().getLatest().stream().map(CmsTemplateContextWebAssembler::toRes).toList())
                        .build())
                .build();
    }

    private static CmsTemplateItemRes toRes(CmsTemplateItemDTO item) {
        return CmsTemplateItemRes.builder()
                .id(item.getId())
                .source(item.getSource())
                .title(item.getTitle())
                .slug(item.getSlug())
                .summary(item.getSummary())
                .excerpt(item.getExcerpt())
                .url(item.getUrl())
                .content(item.getContent())
                .htmlContent(item.getHtmlContent())
                .markdownContent(item.getMarkdownContent())
                .spaceSlug(item.getSpaceSlug())
                .path(item.getPath())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
