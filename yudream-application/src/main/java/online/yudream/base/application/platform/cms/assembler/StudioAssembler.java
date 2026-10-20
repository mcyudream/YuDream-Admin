package online.yudream.base.application.platform.cms.assembler;

import online.yudream.base.application.platform.cms.dto.*;
import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.domain.platform.cms.valobj.StudioRelease;

public final class StudioAssembler {
    private StudioAssembler() {}
    public static StudioStateDTO toDTO(StudioWorkspace workspace) {
        return new StudioStateDTO(workspace.themeCode(), Long.toString(workspace.revision()),
                workspace.documentJson(), workspace.publishedReleaseId(), workspace.updatedAt());
    }
    public static StudioReleaseDTO toDTO(StudioRelease release) {
        return new StudioReleaseDTO(release.id(), Long.toString(release.revision()), release.createdAt());
    }
    public static StudioPublicDocumentDTO toPublicDTO(StudioWorkspace workspace) {
        StudioRelease release = workspace.published();
        return release == null ? null : new StudioPublicDocumentDTO(workspace.themeCode(), release.id(), release.documentJson());
    }
}
