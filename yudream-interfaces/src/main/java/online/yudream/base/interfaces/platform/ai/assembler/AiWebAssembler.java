package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.dto.CmsPageGenerateDTO;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;

public class AiWebAssembler {

    private AiWebAssembler() {
    }

    public static CmsPageGenerateCmd toCmd(CmsPageGenerateRequest request) {
        CmsPageGenerateCmd cmd = new CmsPageGenerateCmd();
        cmd.setTitle(request.getTitle());
        cmd.setPrompt(request.getPrompt());
        cmd.setPageType(request.getPageType());
        cmd.setStyle(request.getStyle());
        cmd.setSiteName(request.getSiteName());
        return cmd;
    }

    public static CmsPageGenerateRes toRes(CmsPageGenerateDTO dto) {
        return CmsPageGenerateRes.builder()
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .htmlContent(dto.getHtmlContent())
                .cssContent(dto.getCssContent())
                .builderProjectJson(dto.getBuilderProjectJson())
                .markdownContent(dto.getMarkdownContent())
                .build();
    }
}
