package online.yudream.base.interfaces.platform.chat.assembler;

import online.yudream.base.application.platform.workspace.cmd.WorkspaceConfigCmd;
import online.yudream.base.application.platform.workspace.dto.WorkspaceConfigDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceFileContentDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceOverviewDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceTrashDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceVersionDTO;
import online.yudream.base.interfaces.platform.chat.request.WorkspaceConfigRequest;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceConfigRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceEntryRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceFileContentRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceOverviewRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceTrashRes;
import online.yudream.base.interfaces.platform.chat.res.WorkspaceVersionRes;

import java.util.List;

public final class WorkspaceWebAssembler {

    private WorkspaceWebAssembler() {
    }

    public static WorkspaceOverviewRes overview(WorkspaceOverviewDTO dto) {
        return new WorkspaceOverviewRes(dto.workspaceId(), dto.uri(), dto.entries().stream().map(WorkspaceWebAssembler::entry).toList(),
                dto.usedBytes(), dto.quotaBytes(), dto.tmpRetentionDays(), dto.trashRetentionDays());
    }

    public static WorkspaceEntryRes entry(WorkspaceEntryDTO dto) {
        return new WorkspaceEntryRes(dto.id(), dto.path(), dto.kind(), dto.size(), dto.contentType(), dto.revision(),
                dto.updatedBy(), dto.updatedAt());
    }

    public static WorkspaceFileContentRes content(WorkspaceFileContentDTO dto) {
        return new WorkspaceFileContentRes(dto.path(), dto.encoding(), dto.content(), dto.truncated(), dto.size(),
                dto.contentType(), dto.revision());
    }

    public static List<WorkspaceTrashRes> trash(List<WorkspaceTrashDTO> items) {
        return items.stream().map(dto -> new WorkspaceTrashRes(dto.id(), dto.path(), dto.kind(), dto.size(), dto.childCount(),
                dto.deletedAt(), dto.expiresAt())).toList();
    }

    public static List<WorkspaceVersionRes> versions(List<WorkspaceVersionDTO> items) {
        return items.stream().map(dto -> new WorkspaceVersionRes(dto.id(), dto.revision(), dto.size(), dto.actor(),
                dto.createdAt(), dto.current())).toList();
    }

    public static WorkspaceConfigCmd config(WorkspaceConfigRequest request) {
        WorkspaceConfigCmd cmd = new WorkspaceConfigCmd();
        cmd.setQuotaMb(request.getQuotaMb());
        cmd.setTmpRetentionDays(request.getTmpRetentionDays());
        return cmd;
    }

    public static WorkspaceConfigRes config(WorkspaceConfigDTO dto) {
        return new WorkspaceConfigRes(dto.quotaMb(), dto.tmpRetentionDays());
    }
}
