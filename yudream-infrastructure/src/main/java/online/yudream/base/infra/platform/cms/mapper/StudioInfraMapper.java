package online.yudream.base.infra.platform.cms.mapper;

import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.domain.platform.cms.valobj.StudioRelease;
import online.yudream.base.infra.platform.cms.dataobj.StudioWorkspaceDO;
import java.util.List;

public final class StudioInfraMapper {
    private StudioInfraMapper() {}
    public static StudioWorkspace toDomain(StudioWorkspaceDO data) {
        if (data == null) return null;
        return new StudioWorkspace(data.getThemeCode(), data.getRevision(), data.getDocumentJson(), data.getPublishedReleaseId(), data.getUpdatedAt(),
                data.getReleases() == null ? List.of() : data.getReleases().stream().map(item -> new StudioRelease(item.getId(), item.getRevision(), item.getDocumentJson(), item.getCreatedAt())).toList());
    }
    public static StudioWorkspaceDO toDataObj(StudioWorkspace workspace) {
        var data = new StudioWorkspaceDO();
        data.setThemeCode(workspace.themeCode()); data.setRevision(workspace.revision());
        data.setDocumentJson(workspace.documentJson()); data.setPublishedReleaseId(workspace.publishedReleaseId());
        data.setUpdatedAt(workspace.updatedAt());
        data.setReleases(workspace.releases().stream().map(item -> {
            var release = new StudioWorkspaceDO.ReleaseDO();
            release.setId(item.id()); release.setRevision(item.revision());
            release.setDocumentJson(item.documentJson()); release.setCreatedAt(item.createdAt());
            return release;
        }).toList());
        return data;
    }
}
