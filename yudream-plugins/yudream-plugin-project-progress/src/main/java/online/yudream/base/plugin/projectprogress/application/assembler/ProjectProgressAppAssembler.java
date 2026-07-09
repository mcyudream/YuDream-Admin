package online.yudream.base.plugin.projectprogress.application.assembler;

import online.yudream.base.plugin.projectprogress.application.dto.ProjectAcceptanceDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectCheckInDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectProgressEventDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectProgressProjectDTO;
import online.yudream.base.plugin.projectprogress.application.dto.ProjectWorkDetailDTO;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectAcceptanceRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectCheckInRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressEvent;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressProject;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectWorkDetail;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectFileEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectLocationEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftPolicy;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectStatusOption;

public class ProjectProgressAppAssembler {

    public ProjectProgressProjectDTO toDTO(ProjectProgressProject project) {
        ProjectMinecraftPolicy minecraft = project.minecraftPolicy();
        return new ProjectProgressProjectDTO(project.id(), project.name(), project.description(), project.managerUserIds(),
                project.memberUserIds(), project.statuses().stream().map(this::toDTO).toList(), project.defaultStatusCode(),
                project.doneStatusCode(), project.reworkStatusCode(), project.minCheckInIntervalMinutes(),
                project.allowedCheckInTypes().stream().map(Enum::name).toList(),
                new ProjectProgressProjectDTO.MinecraftPolicyDTO(minecraft.enabled(), minecraft.serverId(),
                        minecraft.requiredOnlineMinutes(), minecraft.includeAfk(), minecraft.autoCheckInEnabled()),
                project.enabled(), project.createdAt(), project.updatedAt());
    }

    public ProjectWorkDetailDTO toDTO(ProjectWorkDetail detail) {
        return new ProjectWorkDetailDTO(detail.id(), detail.projectId(), detail.title(), detail.description(),
                detail.statusCode(), detail.assignmentMode().name(), detail.requiredAssigneeCount(),
                detail.candidateUserIds(), detail.assigneeUserIds(), detail.acceptorUserIds(), detail.published(),
                detail.pendingAcceptance(), detail.acceptanceSummary(), detail.acceptanceFiles().stream().map(this::toDetailFileDTO).toList(),
                detail.dueAt(), detail.createdAt(), detail.updatedAt());
    }

    public ProjectCheckInDTO toDTO(ProjectCheckInRecord record) {
        ProjectLocationEvidence location = record.location();
        ProjectMinecraftEvidence minecraft = record.minecraft();
        return new ProjectCheckInDTO(record.id(), record.projectId(), record.detailId(), record.userId(),
                record.type().name(), record.summary(), record.files().stream().map(this::toDTO).toList(),
                location == null ? null : new ProjectCheckInDTO.LocationDTO(location.address(), location.latitude(), location.longitude()),
                minecraft == null ? null : new ProjectCheckInDTO.MinecraftEvidenceDTO(minecraft.serverId(), minecraft.playerId(),
                        minecraft.playerName(), minecraft.totalOnlineMillis(), minecraft.totalAfkMillis(), minecraft.effectiveOnlineMillis()),
                record.createdAt());
    }

    public ProjectAcceptanceDTO toDTO(ProjectAcceptanceRecord record) {
        return new ProjectAcceptanceDTO(record.id(), record.projectId(), record.detailId(), record.operatorUserId(),
                record.result().name(), record.fromStatusCode(), record.toStatusCode(), record.reason(), record.createdAt());
    }

    public ProjectProgressEventDTO toDTO(ProjectProgressEvent event) {
        return new ProjectProgressEventDTO(event.id(), event.projectId(), event.detailId(), event.operatorUserId(),
                event.type().name(), event.message(), event.metadata(), event.createdAt());
    }

    private ProjectProgressProjectDTO.StatusDTO toDTO(ProjectStatusOption status) {
        return new ProjectProgressProjectDTO.StatusDTO(status.code(), status.label(), status.terminal(), status.sort());
    }

    private ProjectCheckInDTO.FileEvidenceDTO toDTO(ProjectFileEvidence file) {
        return new ProjectCheckInDTO.FileEvidenceDTO(file.objectKey(), file.filename(), file.contentType(), file.size(), file.image());
    }

    private ProjectWorkDetailDTO.FileEvidenceDTO toDetailFileDTO(ProjectFileEvidence file) {
        return new ProjectWorkDetailDTO.FileEvidenceDTO(file.objectKey(), file.filename(), file.contentType(), file.size(), file.image());
    }
}
