package online.yudream.base.plugin.projectprogress.application.service;

import online.yudream.base.plugin.projectprogress.application.assembler.ProjectProgressAppAssembler;
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
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectAcceptanceRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectCheckInRecord;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressEvent;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressProject;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectWorkDetail;
import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectAcceptanceResult;
import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectAssignmentMode;
import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectCheckInType;
import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectProgressEventType;
import online.yudream.base.plugin.projectprogress.domain.repo.ProjectProgressRepository;
import online.yudream.base.plugin.projectprogress.domain.service.ProjectAssignmentService;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectFileEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectLocationEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftPolicy;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectStatusOption;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class ProjectProgressAppService {

    private static final int SCAN_PAGE_SIZE = 200;

    private final ProjectProgressRepository repository;
    private final PluginFileStore files;
    private final ProjectProgressNotificationService notifications;
    private final ProjectProgressMinecraftService minecraft;
    private final ProjectProgressEventStream eventStream = new ProjectProgressEventStream();
    private final ProjectAssignmentService assignmentService = new ProjectAssignmentService();
    private final ProjectProgressAppAssembler assembler = new ProjectProgressAppAssembler();

    public ProjectProgressAppService(ProjectProgressRepository repository, PluginFileStore files, FrameworkServices framework) {
        this.repository = repository;
        this.files = files;
        this.notifications = new ProjectProgressNotificationService(framework);
        this.minecraft = new ProjectProgressMinecraftService(framework);
    }

    public ProjectProgressStatusDTO status() {
        return new ProjectProgressStatusDTO(minecraft.ready(), true);
    }

    public List<ProjectProgressProjectDTO> projects(int page, int size) {
        return repository.listProjects(safePage(page), safeSize(size)).stream().map(assembler::toDTO).toList();
    }

    public ProjectProgressProjectDTO project(String projectId) {
        return assembler.toDTO(requireProject(projectId));
    }

    public ProjectProgressProjectDTO createProject(ProjectProgressProjectSaveCmd cmd, String operatorUserId) {
        ProjectProgressProject project = ProjectProgressProject.create(cmd.name(), cmd.description(), managers(cmd.managerUserIds(), operatorUserId),
                cmd.memberUserIds(), statuses(cmd.statuses()), cmd.defaultStatusCode(), cmd.doneStatusCode(),
                cmd.reworkStatusCode(), intValue(cmd.minCheckInIntervalMinutes(), 0), checkInTypes(cmd.allowedCheckInTypes()),
                minecraftPolicy(cmd.minecraftPolicy()), cmd.enabled() == null || cmd.enabled());
        ProjectProgressProject saved = repository.saveProject(project);
        event(saved.id(), "", operatorUserId, ProjectProgressEventType.PROJECT_SAVED, "项目已创建", Map.of("projectName", saved.name()));
        return assembler.toDTO(saved);
    }

    public ProjectProgressProjectDTO updateProject(String projectId, ProjectProgressProjectSaveCmd cmd, String operatorUserId) {
        ProjectProgressProject existing = requireProject(projectId);
        ProjectProgressProject saved = repository.saveProject(existing.update(cmd.name(), cmd.description(), managers(cmd.managerUserIds(), operatorUserId),
                cmd.memberUserIds(), statuses(cmd.statuses()), cmd.defaultStatusCode(), cmd.doneStatusCode(),
                cmd.reworkStatusCode(), intValue(cmd.minCheckInIntervalMinutes(), 0), checkInTypes(cmd.allowedCheckInTypes()),
                minecraftPolicy(cmd.minecraftPolicy()), cmd.enabled() == null || cmd.enabled()));
        event(saved.id(), "", operatorUserId, ProjectProgressEventType.PROJECT_SAVED, "项目已更新", Map.of("projectName", saved.name()));
        return assembler.toDTO(saved);
    }

    public void deleteProject(String projectId) {
        requireProject(projectId);
        repository.deleteProject(projectId);
    }

    public List<ProjectWorkDetailDTO> details(String projectId, int page, int size) {
        requireProject(projectId);
        return repository.listDetails(projectId, safePage(page), safeSize(size)).stream().map(assembler::toDTO).toList();
    }

    public List<ProjectWorkDetailDTO> myTasks(String userId, int page, int size) {
        return repository.listDetailsByAssignee(requireText(userId, "请先登录"), safePage(page), safeSize(size)).stream()
                .map(assembler::toDTO)
                .toList();
    }

    public List<ProjectWorkDetailDTO> pendingAcceptance(String userId, int page, int size) {
        return repository.listPendingAcceptance(requireText(userId, "请先登录"), safePage(page), safeSize(size)).stream()
                .map(assembler::toDTO)
                .toList();
    }

    public List<ProjectWorkDetailDTO> claimableTasks(String userId, int page, int size) {
        String safeUserId = requireText(userId, "请先登录");
        return repository.listClaimableDetails(safeUserId, safePage(page), safeSize(size)).stream()
                .filter(detail -> repository.findProject(detail.projectId()).map(ProjectProgressProject::enabled).orElse(false))
                .map(assembler::toDTO)
                .toList();
    }

    public ProjectWorkDetailDTO createDetail(String projectId, ProjectProgressDetailSaveCmd cmd, String operatorUserId) {
        ProjectProgressProject project = requireProject(projectId);
        ProjectAssignmentMode assignmentMode = ProjectAssignmentMode.of(cmd.assignmentMode());
        ProjectWorkDetail detail = ProjectWorkDetail.create(project.id(), cmd.title(), cmd.description(),
                firstText(cmd.statusCode(), project.defaultStatusCode()), assignmentMode,
                intValue(cmd.requiredAssigneeCount(), 1), candidatePool(cmd.candidateUserIds(), project, assignmentMode),
                cmd.assigneeUserIds(), cmd.acceptorUserIds(), cmd.dueAt());
        ProjectWorkDetail saved = repository.saveDetail(detail);
        event(project.id(), saved.id(), operatorUserId, ProjectProgressEventType.DETAIL_SAVED, "工作细节已创建", Map.of("title", saved.title()));
        return assembler.toDTO(saved);
    }

    public ProjectWorkDetailDTO updateDetail(String detailId, ProjectProgressDetailSaveCmd cmd, String operatorUserId) {
        ProjectWorkDetail existing = requireDetail(detailId);
        ProjectProgressProject project = requireProject(existing.projectId());
        ProjectAssignmentMode assignmentMode = ProjectAssignmentMode.of(cmd.assignmentMode());
        ProjectWorkDetail saved = repository.saveDetail(existing.update(cmd.title(), cmd.description(),
                firstText(cmd.statusCode(), existing.statusCode()), assignmentMode,
                intValue(cmd.requiredAssigneeCount(), existing.requiredAssigneeCount()), candidatePool(cmd.candidateUserIds(), project, assignmentMode),
                cmd.assigneeUserIds(), cmd.acceptorUserIds(), cmd.published(), cmd.dueAt()));
        event(project.id(), saved.id(), operatorUserId, ProjectProgressEventType.DETAIL_SAVED, "工作细节已更新", Map.of("title", saved.title()));
        return assembler.toDTO(saved);
    }

    public void deleteDetail(String detailId) {
        requireDetail(detailId);
        repository.deleteDetail(detailId);
    }

    public ProjectWorkDetailDTO publishDetail(String detailId, String operatorUserId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        List<String> assignees = detail.assigneeUserIds();
        if (detail.assignmentMode() == ProjectAssignmentMode.RANDOM && assignees.isEmpty()) {
            assignees = assignmentService.randomAssignees(emptyToMembers(detail.candidateUserIds(), project), detail.requiredAssigneeCount());
        }
        ProjectWorkDetail saved = repository.saveDetail(detail.publish(assignees));
        safeNotifyAssigned(project, saved, assignees);
        event(project.id(), saved.id(), operatorUserId, ProjectProgressEventType.DETAIL_PUBLISHED, "工作细节已发布", Map.of("title", saved.title()));
        return assembler.toDTO(saved);
    }

    public ProjectWorkDetailDTO randomAssign(String detailId, String operatorUserId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        List<String> assignees = assignmentService.randomAssignees(emptyToMembers(detail.candidateUserIds(), project), detail.requiredAssigneeCount());
        ProjectWorkDetail saved = repository.saveDetail(detail.assign(assignees));
        safeNotifyAssigned(project, saved, assignees);
        event(project.id(), saved.id(), operatorUserId, ProjectProgressEventType.DETAIL_ASSIGNED, "工作细节已随机分配", Map.of("assigneeUserIds", assignees));
        return assembler.toDTO(saved);
    }

    public ProjectWorkDetailDTO claim(String detailId, String userId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        if (!project.enabled()) {
            throw new IllegalArgumentException("项目未启用，暂不能认领任务");
        }
        ProjectWorkDetail saved = repository.saveDetail(detail.claim(userId));
        safeNotifyAssigned(project, saved, List.of(userId));
        event(project.id(), saved.id(), userId, ProjectProgressEventType.DETAIL_CLAIMED, "工作细节已认领", Map.of("userId", userId));
        return assembler.toDTO(saved);
    }

    public List<ProjectCheckInDTO> checkIns(String detailId, int page, int size) {
        requireDetail(detailId);
        return repository.listCheckIns(detailId, safePage(page), safeSize(size)).stream().map(assembler::toDTO).toList();
    }

    public ProjectCheckInDTO checkIn(String detailId, ProjectProgressCheckInCmd cmd, String userId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        ProjectCheckInType type = ProjectCheckInType.of(cmd.type());
        ensureCanCheckIn(project, detail, userId, type);
        ProjectCheckInRecord saved = repository.saveCheckIn(ProjectCheckInRecord.create(project.id(), detail.id(), userId,
                type, cmd.summary(), fileEvidence(project.id(), detail.id(), userId, cmd.files()),
                location(cmd.location()), null));
        event(project.id(), detail.id(), userId, ProjectProgressEventType.CHECK_IN_CREATED, "用户已打卡", Map.of("type", type.name()));
        return assembler.toDTO(saved);
    }

    public ProjectCheckInDTO minecraftCheckIn(String detailId, String userId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        ensureCanCheckIn(project, detail, userId, ProjectCheckInType.MINECRAFT_ONLINE);
        ProjectMinecraftEvidence evidence = minecraft.requireEvidence(project.minecraftPolicy(), userId);
        ProjectCheckInRecord saved = repository.saveCheckIn(ProjectCheckInRecord.create(project.id(), detail.id(), userId,
                ProjectCheckInType.MINECRAFT_ONLINE, "Minecraft 在线时长自动打卡", List.of(), null, evidence));
        event(project.id(), detail.id(), userId, ProjectProgressEventType.MINECRAFT_CHECK_IN_CREATED, "Minecraft 在线时长打卡已生成", Map.of("userId", userId));
        return assembler.toDTO(saved);
    }

    public List<ProjectCheckInDTO> autoMinecraftCheckIns(String projectId) {
        ProjectProgressProject project = requireProject(projectId);
        if (!project.minecraftPolicy().enabled() || !project.minecraftPolicy().autoCheckInEnabled()) {
            throw new IllegalArgumentException("该项目未启用 Minecraft 自动打卡");
        }
        List<ProjectCheckInDTO> result = new java.util.ArrayList<>();
        for (ProjectWorkDetail detail : allDetails(project.id())) {
            for (String userId : detail.assigneeUserIds()) {
                try {
                    result.add(minecraftCheckIn(detail.id(), userId));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return result;
    }

    public ProjectAcceptanceDTO accept(String detailId, ProjectProgressAcceptanceCmd cmd, String operatorUserId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        ensureCanAccept(detail, project, operatorUserId);
        String fromStatus = detail.statusCode();
        String toStatus = firstText(cmd.toStatusCode(), project.doneStatusCode());
        ProjectWorkDetail saved = repository.saveDetail(detail.withStatus(toStatus));
        ProjectAcceptanceRecord record = repository.saveAcceptanceRecord(ProjectAcceptanceRecord.create(project.id(), detail.id(),
                operatorUserId, ProjectAcceptanceResult.ACCEPTED, fromStatus, saved.statusCode(), cmd.reason()));
        event(project.id(), detail.id(), operatorUserId, ProjectProgressEventType.DETAIL_ACCEPTED, "工作细节已验收通过", Map.of("toStatusCode", toStatus));
        return assembler.toDTO(record);
    }

    public ProjectAcceptanceDTO reject(String detailId, ProjectProgressAcceptanceCmd cmd, String operatorUserId) {
        ProjectWorkDetail detail = requireDetail(detailId);
        ProjectProgressProject project = requireProject(detail.projectId());
        ensureCanAccept(detail, project, operatorUserId);
        String fromStatus = detail.statusCode();
        String toStatus = firstText(cmd.toStatusCode(), firstText(project.reworkStatusCode(), project.defaultStatusCode()));
        ProjectWorkDetail saved = repository.saveDetail(detail.withStatus(toStatus));
        safeNotifyRework(project, saved, detail.assigneeUserIds(), cmd.reason());
        ProjectAcceptanceRecord record = repository.saveAcceptanceRecord(ProjectAcceptanceRecord.create(project.id(), detail.id(),
                operatorUserId, ProjectAcceptanceResult.REJECTED, fromStatus, saved.statusCode(), cmd.reason()));
        event(project.id(), detail.id(), operatorUserId, ProjectProgressEventType.DETAIL_REJECTED, "工作细节验收未通过", Map.of("toStatusCode", toStatus));
        return assembler.toDTO(record);
    }

    public List<ProjectAcceptanceDTO> acceptanceRecords(String detailId, int page, int size) {
        requireDetail(detailId);
        return repository.listAcceptanceRecords(detailId, safePage(page), safeSize(size)).stream().map(assembler::toDTO).toList();
    }

    public List<ProjectProgressEventDTO> events(String projectId, Long since, int page, int size) {
        requireProject(projectId);
        return repository.listEvents(projectId, since, safePage(page), safeSize(size)).stream().map(assembler::toDTO).toList();
    }

    public ProjectProgressEventStream eventStream() {
        return eventStream;
    }

    private void ensureCanCheckIn(ProjectProgressProject project, ProjectWorkDetail detail, String userId, ProjectCheckInType type) {
        String safeUserId = requireText(userId, "请先登录");
        if (!project.allows(type)) {
            throw new IllegalArgumentException("项目未允许该打卡方式：" + type.name());
        }
        if (!detail.assignedTo(safeUserId)) {
            throw new IllegalArgumentException("当前用户不是该工作细节负责人");
        }
        repository.latestCheckIn(detail.id(), safeUserId).ifPresent(latest -> {
            long intervalMillis = project.minCheckInIntervalMinutes() * 60_000L;
            if (intervalMillis > 0 && System.currentTimeMillis() - latest.createdAt() < intervalMillis) {
                throw new IllegalArgumentException("未达到项目要求的最小打卡间隔");
            }
        });
    }

    private void ensureCanAccept(ProjectWorkDetail detail, ProjectProgressProject project, String operatorUserId) {
        String safeUserId = requireText(operatorUserId, "请先登录");
        if (!detail.canAccept(safeUserId, project)) {
            throw new IllegalArgumentException("当前用户没有验收该工作细节的权限");
        }
    }

    private List<ProjectFileEvidence> fileEvidence(String projectId, String detailId, String userId, List<ProjectProgressCheckInCmd.FileEvidence> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ProjectFileEvidence> result = new java.util.ArrayList<>();
        int index = 0;
        for (ProjectProgressCheckInCmd.FileEvidence item : items) {
            byte[] content = decodeBase64(item.base64());
            String filename = firstText(item.filename(), "evidence-" + index);
            String contentType = firstText(item.contentType(), "application/octet-stream");
            String objectKey = "check-ins/" + projectId + "/" + detailId + "/" + userId + "/" + System.currentTimeMillis() + "-" + index + "-" + sanitize(filename);
            files.put(objectKey, new ByteArrayInputStream(content), content.length, contentType);
            result.add(new ProjectFileEvidence(objectKey, filename, contentType, content.length, Boolean.TRUE.equals(item.image())));
            index++;
        }
        return result;
    }

    private ProjectLocationEvidence location(ProjectProgressCheckInCmd.Location location) {
        return location == null ? null : new ProjectLocationEvidence(location.address(), location.latitude(), location.longitude());
    }

    private byte[] decodeBase64(String value) {
        String safeValue = requireText(value, "文件内容不能为空");
        int commaIndex = safeValue.indexOf(',');
        if (commaIndex >= 0) {
            safeValue = safeValue.substring(commaIndex + 1);
        }
        return Base64.getDecoder().decode(safeValue);
    }

    private void safeNotifyAssigned(ProjectProgressProject project, ProjectWorkDetail detail, List<String> assignees) {
        try {
            notifications.notifyAssigned(project, detail, assignees);
        } catch (RuntimeException ignored) {
        }
    }

    private void safeNotifyRework(ProjectProgressProject project, ProjectWorkDetail detail, List<String> assignees, String reason) {
        try {
            notifications.notifyRework(project, detail, assignees, reason);
        } catch (RuntimeException ignored) {
        }
    }

    private ProjectProgressEvent event(String projectId, String detailId, String operatorUserId, ProjectProgressEventType type,
                                       String message, Map<String, Object> metadata) {
        ProjectProgressEvent saved = repository.saveEvent(ProjectProgressEvent.create(projectId, detailId, operatorUserId, type, message, metadata));
        eventStream.publish(saved);
        return saved;
    }

    private ProjectProgressProject requireProject(String projectId) {
        return repository.findProject(requireText(projectId, "项目 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("项目不存在：" + projectId));
    }

    private ProjectWorkDetail requireDetail(String detailId) {
        return repository.findDetail(requireText(detailId, "工作细节 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("工作细节不存在：" + detailId));
    }

    private List<ProjectWorkDetail> allDetails(String projectId) {
        List<ProjectWorkDetail> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<ProjectWorkDetail> batch = repository.listDetails(projectId, page, SCAN_PAGE_SIZE);
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private List<ProjectStatusOption> statuses(List<ProjectProgressProjectSaveCmd.Status> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return ProjectProgressProject.defaultStatuses();
        }
        return statuses.stream()
                .map(status -> new ProjectStatusOption(status.code(), status.label(), Boolean.TRUE.equals(status.terminal()),
                        status.sort() == null ? 0 : status.sort()))
                .toList();
    }

    private List<ProjectCheckInType> checkInTypes(List<String> values) {
        return values == null || values.isEmpty()
                ? List.of(ProjectCheckInType.IMAGE, ProjectCheckInType.FILE, ProjectCheckInType.LOCATION)
                : values.stream().map(ProjectCheckInType::of).toList();
    }

    private ProjectMinecraftPolicy minecraftPolicy(ProjectProgressProjectSaveCmd.MinecraftPolicy policy) {
        if (policy == null) {
            return ProjectMinecraftPolicy.disabled();
        }
        return new ProjectMinecraftPolicy(Boolean.TRUE.equals(policy.enabled()), policy.serverId(),
                intValue(policy.requiredOnlineMinutes(), 0), Boolean.TRUE.equals(policy.includeAfk()),
                Boolean.TRUE.equals(policy.autoCheckInEnabled()));
    }

    private List<String> emptyToMembers(List<String> candidates, ProjectProgressProject project) {
        return candidates == null || candidates.isEmpty() ? project.memberUserIds() : candidates;
    }

    private List<String> candidatePool(List<String> candidates, ProjectProgressProject project, ProjectAssignmentMode assignmentMode) {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }
        return assignmentMode == ProjectAssignmentMode.RANDOM ? project.memberUserIds() : List.of();
    }

    private List<String> managers(List<String> managerUserIds, String operatorUserId) {
        String owner = requireText(operatorUserId, "请先登录");
        List<String> result = new java.util.ArrayList<>();
        if (managerUserIds != null) {
            managerUserIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(result::add);
        }
        if (!result.contains(owner)) {
            result.add(0, owner);
        }
        return result.stream().distinct().toList();
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.max(Math.min(size <= 0 ? 20 : size, 200), 1);
    }

    private int intValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second;
    }

    private String sanitize(String filename) {
        return filename == null ? "file" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
