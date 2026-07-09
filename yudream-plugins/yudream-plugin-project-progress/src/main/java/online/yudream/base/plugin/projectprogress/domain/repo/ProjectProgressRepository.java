package online.yudream.base.plugin.projectprogress.domain.repo;

import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectAcceptanceRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectCheckInRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressEvent;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressProject;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectWorkDetail;

import java.util.List;
import java.util.Optional;

public interface ProjectProgressRepository {

    ProjectProgressProject saveProject(ProjectProgressProject project);

    Optional<ProjectProgressProject> findProject(String projectId);

    List<ProjectProgressProject> listProjects(int page, int size);

    void deleteProject(String projectId);

    ProjectWorkDetail saveDetail(ProjectWorkDetail detail);

    Optional<ProjectWorkDetail> findDetail(String detailId);

    List<ProjectWorkDetail> listDetails(String projectId, int page, int size);

    List<ProjectWorkDetail> listDetailsByAssignee(String userId, int page, int size);

    List<ProjectWorkDetail> listClaimableDetails(String userId, int page, int size);

    List<ProjectWorkDetail> listPendingAcceptance(String userId, int page, int size);

    void deleteDetail(String detailId);

    ProjectCheckInRecord saveCheckIn(ProjectCheckInRecord record);

    List<ProjectCheckInRecord> listCheckIns(String detailId, int page, int size);

    Optional<ProjectCheckInRecord> latestCheckIn(String detailId, String userId);

    List<ProjectCheckInRecord> listProjectCheckIns(String projectId, int page, int size);

    Optional<ProjectCheckInRecord> latestProjectCheckIn(String projectId, String userId);

    ProjectAcceptanceRecord saveAcceptanceRecord(ProjectAcceptanceRecord record);

    List<ProjectAcceptanceRecord> listAcceptanceRecords(String detailId, int page, int size);

    ProjectProgressEvent saveEvent(ProjectProgressEvent event);

    List<ProjectProgressEvent> listEvents(String projectId, Long since, int page, int size);
}
