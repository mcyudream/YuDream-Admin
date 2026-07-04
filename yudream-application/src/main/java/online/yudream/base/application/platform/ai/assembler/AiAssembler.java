package online.yudream.base.application.platform.ai.assembler;

import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;

public class AiAssembler {

    private AiAssembler() {
    }

    public static CmsPageGenerateDTO toDTO(AiGenerationResult result) {
        return CmsPageGenerateDTO.builder()
                .title(result.title())
                .summary(result.summary())
                .htmlContent(result.htmlContent())
                .cssContent(result.cssContent())
                .builderProjectJson(result.builderProjectJson())
                .markdownContent(result.markdownContent())
                .build();
    }
}
