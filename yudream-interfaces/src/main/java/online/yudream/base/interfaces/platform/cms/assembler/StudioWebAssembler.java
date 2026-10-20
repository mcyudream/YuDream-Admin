package online.yudream.base.interfaces.platform.cms.assembler;

import online.yudream.base.application.platform.cms.cmd.*;
import online.yudream.base.application.platform.cms.dto.*;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.cms.request.*;
import online.yudream.base.interfaces.platform.cms.res.*;

public final class StudioWebAssembler {
    private StudioWebAssembler() {}
    public static StudioDraftSaveCmd toCmd(StudioDraftSaveRequest request) {
        return new StudioDraftSaveCmd(request.expectedRevision(), request.documentJson());
    }
    public static StudioRevisionCmd toCmd(StudioRevisionRequest request) {
        return new StudioRevisionCmd(request.expectedRevision());
    }
    public static StudioStateRes toRes(StudioStateDTO dto) {
        return new StudioStateRes(dto.themeCode(), dto.revision(), dto.documentJson(), dto.publishedReleaseId(), dto.updatedAt());
    }
    public static StudioReleasePageRes toRes(PageResult<StudioReleaseDTO> page) {
        return new StudioReleasePageRes(page.getRecords().stream().map(dto -> new StudioReleaseRes(dto.id(), dto.revision(), dto.createdAt())).toList(), page.getTotal());
    }
    public static StudioPublicDocumentRes toRes(StudioPublicDocumentDTO dto) {
        return dto == null ? null : new StudioPublicDocumentRes(dto.themeCode(), dto.releaseId(), dto.documentJson());
    }
}
