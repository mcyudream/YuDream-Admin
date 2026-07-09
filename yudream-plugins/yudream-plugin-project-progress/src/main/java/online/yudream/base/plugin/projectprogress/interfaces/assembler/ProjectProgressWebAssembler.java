package online.yudream.base.plugin.projectprogress.interfaces.assembler;

import online.yudream.base.plugin.projectprogress.application.cmd.ProjectProgressAcceptanceCmd;
import online.yudream.base.plugin.projectprogress.application.cmd.ProjectProgressCheckInCmd;
import online.yudream.base.plugin.projectprogress.application.cmd.ProjectProgressDetailSaveCmd;
import online.yudream.base.plugin.projectprogress.application.cmd.ProjectProgressProjectSaveCmd;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectAcceptanceDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectCheckInDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectProgressEventDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectProgressProjectDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectProgressStatusDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectWorkDetailDTO;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressAcceptanceRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressCheckInRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressDetailSaveRequest;
import online.yudream.base.plugin.projectprogress.interfaces.request.ProjectProgressProjectSaveRequest;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectAcceptanceRes;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectCheckInRes;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectProgressEventRes;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectProgressProjectRes;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectProgressStatusRes;
import online.yudream.base.plugin.projectprogress.interfaces.res.ProjectWorkDetailRes;

public class ProjectProgressWebAssembler {

    public ProjectProgressProjectSaveCmd toCmd(ProjectProgressProjectSaveRequest request) {
        return new ProjectProgressProjectSaveCmd(request.name(), request.description(), request.managerUserIds(),
                request.memberUserIds(), request.statuses() == null ? null : request.statuses().stream()
                .map(item -> new ProjectProgressProjectSaveCmd.Status(item.code(), item.label(), item.terminal(), item.sort()))
                .toList(), request.defaultStatusCode(), request.doneStatusCode(), request.reworkStatusCode(),
                request.minCheckInIntervalMinutes(), request.allowedCheckInTypes(),
                request.minecraftPolicy() == null ? null : new ProjectProgressProjectSaveCmd.MinecraftPolicy(
                        request.minecraftPolicy().enabled(), request.minecraftPolicy().serverId(),
                        request.minecraftPolicy().requiredOnlineMinutes(), request.minecraftPolicy().includeAfk(),
                        request.minecraftPolicy().autoCheckInEnabled()),
                request.enabled());
    }

    public ProjectProgressDetailSaveCmd toCmd(ProjectProgressDetailSaveRequest request) {
        return new ProjectProgressDetailSaveCmd(request.title(), request.description(), request.statusCode(),
                request.assignmentMode(), request.requiredAssigneeCount(), request.candidateUserIds(),
                request.assigneeUserIds(), request.acceptorUserIds(), request.published(), request.dueAt());
    }

    public ProjectProgressCheckInCmd toCmd(ProjectProgressCheckInRequest request) {
        return new ProjectProgressCheckInCmd(request.type(), request.summary(), request.files() == null ? null : request.files().stream()
                .map(file -> new ProjectProgressCheckInCmd.FileEvidence(file.filename(), file.contentType(), file.base64(), file.image()))
                .toList(), request.location() == null ? null : new ProjectProgressCheckInCmd.Location(
                request.location().address(), request.location().latitude(), request.location().longitude()));
    }

    public ProjectProgressAcceptanceCmd toCmd(ProjectProgressAcceptanceRequest request) {
        return new ProjectProgressAcceptanceCmd(request.reason(), request.toStatusCode());
    }

    public ProjectProgressStatusRes toRes(ProjectProgressStatusDTO dto) {
        return new ProjectProgressStatusRes(dto.minecraftReady(), dto.mailReady());
    }

    public ProjectProgressProjectRes toRes(ProjectProgressProjectDTO dto) {
        return new ProjectProgressProjectRes(dto.id(), dto.name(), dto.description(), dto.managerUserIds(), dto.memberUserIds(),
                dto.statuses().stream().map(item -> new ProjectProgressProjectRes.StatusRes(item.code(), item.label(), item.terminal(), item.sort())).toList(),
                dto.defaultStatusCode(), dto.doneStatusCode(), dto.reworkStatusCode(), dto.minCheckInIntervalMinutes(),
                dto.allowedCheckInTypes(),
                new ProjectProgressProjectRes.MinecraftPolicyRes(dto.minecraftPolicy().enabled(), dto.minecraftPolicy().serverId(),
                        dto.minecraftPolicy().requiredOnlineMinutes(), dto.minecraftPolicy().includeAfk(), dto.minecraftPolicy().autoCheckInEnabled()),
                dto.enabled(), dto.createdAt(), dto.updatedAt());
    }

    public ProjectWorkDetailRes toRes(ProjectWorkDetailDTO dto) {
        return new ProjectWorkDetailRes(dto.id(), dto.projectId(), dto.title(), dto.description(), dto.statusCode(),
                dto.assignmentMode(), dto.requiredAssigneeCount(), dto.candidateUserIds(), dto.assigneeUserIds(),
                dto.acceptorUserIds(), dto.published(), dto.dueAt(), dto.createdAt(), dto.updatedAt());
    }

    public ProjectCheckInRes toRes(ProjectCheckInDTO dto) {
        return new ProjectCheckInRes(dto.id(), dto.projectId(), dto.detailId(), dto.userId(), dto.type(), dto.summary(),
                dto.files().stream().map(file -> new ProjectCheckInRes.FileEvidenceRes(file.objectKey(), file.filename(), file.contentType(), file.size(), file.image())).toList(),
                dto.location() == null ? null : new ProjectCheckInRes.LocationRes(dto.location().address(), dto.location().latitude(), dto.location().longitude()),
                dto.minecraft() == null ? null : new ProjectCheckInRes.MinecraftEvidenceRes(dto.minecraft().serverId(), dto.minecraft().playerId(),
                        dto.minecraft().playerName(), dto.minecraft().totalOnlineMillis(), dto.minecraft().totalAfkMillis(), dto.minecraft().effectiveOnlineMillis()),
                dto.createdAt());
    }

    public ProjectAcceptanceRes toRes(ProjectAcceptanceDTO dto) {
        return new ProjectAcceptanceRes(dto.id(), dto.projectId(), dto.detailId(), dto.operatorUserId(), dto.result(),
                dto.fromStatusCode(), dto.toStatusCode(), dto.reason(), dto.createdAt());
    }

    public ProjectProgressEventRes toRes(ProjectProgressEventDTO dto) {
        return new ProjectProgressEventRes(dto.id(), dto.projectId(), dto.detailId(), dto.operatorUserId(), dto.type(),
                dto.message(), dto.metadata(), dto.createdAt());
    }
}
