package online.yudream.base.interfaces.platform.cms;

import online.yudream.base.application.platform.cms.dto.CmsTemplateContextDTO;
import online.yudream.base.application.platform.cms.service.CmsAppService;
import online.yudream.base.application.platform.cms.service.CmsTemplateContextAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.controller.PublicCmsController;
import online.yudream.base.interfaces.platform.cms.res.CmsTemplateContextRes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCmsControllerTemplateContextTest {

    @Test
    void exposesTemplateContextThroughPublicCmsRoute() {
        CmsTemplateContextAppService templateService = mock(CmsTemplateContextAppService.class);
        CmsTemplateContextDTO context = CmsTemplateContextDTO.builder()
                .cms(CmsTemplateContextDTO.CmsTemplateCmsDTO.builder()
                        .pages(CmsTemplateContextDTO.CmsTemplatePagesDTO.builder().latest(List.of()).build())
                        .build())
                .knowledge(CmsTemplateContextDTO.CmsTemplateKnowledgeDTO.builder()
                        .spaces(List.of()).pages(List.of()).latest(List.of()).build())
                .build();
        when(templateService.query()).thenReturn(context);
        PublicCmsController controller = new PublicCmsController(mock(CmsAppService.class), templateService);

        Result<CmsTemplateContextRes> result = controller.templateContext();

        assertThat(result.getData().getKnowledge().getLatest()).isEmpty();
        verify(templateService).query();
    }
}
