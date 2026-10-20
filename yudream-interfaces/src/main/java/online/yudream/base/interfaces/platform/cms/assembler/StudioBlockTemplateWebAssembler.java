package online.yudream.base.interfaces.platform.cms.assembler;

import online.yudream.base.application.platform.cms.cmd.StudioBlockTemplateSaveCmd;
import online.yudream.base.application.platform.cms.dto.StudioBlockTemplateDTO;
import online.yudream.base.interfaces.platform.cms.request.StudioBlockTemplateSaveRequest;
import online.yudream.base.interfaces.platform.cms.res.StudioBlockTemplateRes;

public final class StudioBlockTemplateWebAssembler {
    private StudioBlockTemplateWebAssembler() {
    }

    public static StudioBlockTemplateSaveCmd toCmd(StudioBlockTemplateSaveRequest request) {
        return new StudioBlockTemplateSaveCmd(request.name(), request.category(), request.description(), request.keywords(),
                request.nodesJson(), request.expectedRevision());
    }

    public static StudioBlockTemplateRes toRes(StudioBlockTemplateDTO dto) {
        return new StudioBlockTemplateRes(dto.id(), dto.name(), dto.category(), dto.description(), dto.keywords(), dto.nodesJson(),
                dto.revision(), dto.createdBy(), dto.createdAt(), dto.updatedAt());
    }
}
