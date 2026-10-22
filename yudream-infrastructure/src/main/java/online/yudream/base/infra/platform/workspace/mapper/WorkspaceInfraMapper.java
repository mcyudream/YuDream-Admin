package online.yudream.base.infra.platform.workspace.mapper;

import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;
import online.yudream.base.infra.platform.workspace.dataobj.UserWorkspaceDO;
import online.yudream.base.infra.platform.workspace.dataobj.WorkspaceFileDO;
import online.yudream.base.infra.platform.workspace.dataobj.WorkspaceFileVersionDO;

public final class WorkspaceInfraMapper {

    private WorkspaceInfraMapper() {
    }

    public static UserWorkspace workspace(UserWorkspaceDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return UserWorkspace.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .ownerUserId(dataObj.getOwnerUserId())
                .build();
    }

    public static WorkspaceFile file(WorkspaceFileDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return WorkspaceFile.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .workspaceId(dataObj.getWorkspaceId())
                .path(dataObj.getPath())
                .kind(dataObj.getKind())
                .size(dataObj.getSize())
                .sha256(dataObj.getSha256())
                .contentType(dataObj.getContentType())
                .revision(dataObj.getRevision())
                .currentVersionId(dataObj.getCurrentVersionId())
                .updatedBy(dataObj.getUpdatedBy())
                .contentUpdatedAt(dataObj.getContentUpdatedAt())
                .deletedAt(dataObj.getDeletedAt())
                .build();
    }

    public static WorkspaceFileDO file(WorkspaceFile domain) {
        WorkspaceFileDO dataObj = new WorkspaceFileDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setWorkspaceId(domain.getWorkspaceId());
        dataObj.setPath(domain.getPath());
        dataObj.setKind(domain.getKind());
        dataObj.setSize(domain.getSize());
        dataObj.setSha256(domain.getSha256());
        dataObj.setContentType(domain.getContentType());
        dataObj.setRevision(domain.getRevision());
        dataObj.setCurrentVersionId(domain.getCurrentVersionId());
        dataObj.setUpdatedBy(domain.getUpdatedBy());
        dataObj.setContentUpdatedAt(domain.getContentUpdatedAt());
        dataObj.setDeletedAt(domain.getDeletedAt());
        return dataObj;
    }

    public static WorkspaceFileVersion version(WorkspaceFileVersionDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return new WorkspaceFileVersion(dataObj.getId(), dataObj.getWorkspaceId(), dataObj.getFileId(), dataObj.getRevision(),
                dataObj.getObjectKey(), dataObj.getSize(), dataObj.getSha256(), dataObj.getContentType(), dataObj.getActor(),
                dataObj.getCreatedAt());
    }

    public static WorkspaceFileVersionDO version(WorkspaceFileVersion domain) {
        WorkspaceFileVersionDO dataObj = new WorkspaceFileVersionDO();
        dataObj.setId(domain.id());
        dataObj.setWorkspaceId(domain.workspaceId());
        dataObj.setFileId(domain.fileId());
        dataObj.setRevision(domain.revision());
        dataObj.setObjectKey(domain.objectKey());
        dataObj.setSize(domain.size());
        dataObj.setSha256(domain.sha256());
        dataObj.setContentType(domain.contentType());
        dataObj.setActor(domain.actor());
        dataObj.setCreatedAt(domain.createdAt());
        return dataObj;
    }
}
