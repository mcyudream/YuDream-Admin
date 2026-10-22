package online.yudream.base.application.platform.workspace.assembler;

import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceVersionDTO;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;

public final class WorkspaceAssembler {

    private WorkspaceAssembler() {
    }

    public static WorkspaceEntryDTO entry(WorkspaceFile file) {
        return new WorkspaceEntryDTO(String.valueOf(file.getId()), file.getPath(), file.getKind().name(), file.getSize(),
                file.getContentType(), file.getRevision(), file.getUpdatedBy() == null ? null : file.getUpdatedBy().name(),
                file.getContentUpdatedAt() == null ? file.getUpdateTime() : file.getContentUpdatedAt());
    }

    /** 相对 root 的条目视图（AI 受限目录） */
    public static WorkspaceEntryDTO entry(WorkspaceFile file, String relativePath) {
        WorkspaceEntryDTO full = entry(file);
        return new WorkspaceEntryDTO(full.id(), relativePath, full.kind(), full.size(), full.contentType(), full.revision(),
                full.updatedBy(), full.updatedAt());
    }

    public static WorkspaceVersionDTO version(WorkspaceFileVersion version, Long currentVersionId) {
        return new WorkspaceVersionDTO(String.valueOf(version.id()), version.revision(), version.size(),
                version.actor() == null ? null : version.actor().name(), version.createdAt(), version.id().equals(currentVersionId));
    }
}
