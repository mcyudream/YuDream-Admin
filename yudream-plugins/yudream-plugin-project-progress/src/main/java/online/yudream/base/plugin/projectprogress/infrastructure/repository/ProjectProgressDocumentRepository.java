package online.yudream.base.plugin.projectprogress.infrastructure.repository;

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
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectFileEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectLocationEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftEvidence;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectMinecraftPolicy;
import online.yudream.base.plugin.projectprogress.domain.valobj.ProjectStatusOption;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectProgressDocumentRepository implements ProjectProgressRepository {

    private static final int SCAN_PAGE_SIZE = 200;
    private static final String PROJECTS = "projects";
    private static final String DETAILS = "details";
    private static final String CHECK_INS = "check-ins";
    private static final String ACCEPTANCE = "acceptance";
    private static final String EVENTS = "events";

    private final PluginDocumentStore documents;

    public ProjectProgressDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public ProjectProgressProject saveProject(ProjectProgressProject project) {
        return toProject(documents.save(PROJECTS, project.id(), projectDocument(project)));
    }

    @Override
    public Optional<ProjectProgressProject> findProject(String projectId) {
        return documents.findById(PROJECTS, projectId).map(this::toProject);
    }

    @Override
    public List<ProjectProgressProject> listProjects(int page, int size) {
        return documents.findAll(PROJECTS, page, size).stream()
                .map(this::toProject)
                .sorted(Comparator.comparingLong(ProjectProgressProject::updatedAt).reversed())
                .toList();
    }

    @Override
    public void deleteProject(String projectId) {
        documents.delete(PROJECTS, projectId);
    }

    @Override
    public ProjectWorkDetail saveDetail(ProjectWorkDetail detail) {
        return toDetail(documents.save(DETAILS, detail.id(), detailDocument(detail)));
    }

    @Override
    public Optional<ProjectWorkDetail> findDetail(String detailId) {
        return documents.findById(DETAILS, detailId).map(this::toDetail);
    }

    @Override
    public List<ProjectWorkDetail> listDetails(String projectId, int page, int size) {
        return documents.findByField(DETAILS, "projectId", projectId, page, size).stream()
                .map(this::toDetail)
                .sorted(Comparator.comparingLong(ProjectWorkDetail::updatedAt).reversed())
                .toList();
    }

    @Override
    public List<ProjectWorkDetail> listDetailsByAssignee(String userId, int page, int size) {
        return allDetails().stream()
                .filter(detail -> detail.assigneeUserIds().contains(userId))
                .sorted(Comparator.comparingLong(ProjectWorkDetail::updatedAt).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<ProjectWorkDetail> listClaimableDetails(String userId, int page, int size) {
        return allDetails().stream()
                .filter(detail -> detail.claimableBy(userId))
                .sorted(Comparator.comparingLong(ProjectWorkDetail::updatedAt).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<ProjectWorkDetail> listPendingAcceptance(String userId, int page, int size) {
        return allDetails().stream()
                .filter(ProjectWorkDetail::published)
                .filter(ProjectWorkDetail::pendingAcceptance)
                .filter(detail -> detail.acceptorUserIds().isEmpty() || detail.acceptorUserIds().contains(userId))
                .sorted(Comparator.comparingLong(ProjectWorkDetail::updatedAt).reversed())
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    @Override
    public void deleteDetail(String detailId) {
        documents.delete(DETAILS, detailId);
    }

    @Override
    public ProjectCheckInRecord saveCheckIn(ProjectCheckInRecord record) {
        return toCheckIn(documents.save(CHECK_INS, record.id(), checkInDocument(record)));
    }

    @Override
    public List<ProjectCheckInRecord> listCheckIns(String detailId, int page, int size) {
        return documents.findByField(CHECK_INS, "detailId", detailId, page, size).stream()
                .map(this::toCheckIn)
                .sorted(Comparator.comparingLong(ProjectCheckInRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<ProjectCheckInRecord> latestCheckIn(String detailId, String userId) {
        return allCheckIns(detailId).stream()
                .filter(record -> userId.equals(record.userId()))
                .max(Comparator.comparingLong(ProjectCheckInRecord::createdAt));
    }

    @Override
    public List<ProjectCheckInRecord> listProjectCheckIns(String projectId, int page, int size) {
        return documents.findByField(CHECK_INS, "projectId", projectId, page, size).stream()
                .map(this::toCheckIn)
                .sorted(Comparator.comparingLong(ProjectCheckInRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<ProjectCheckInRecord> latestProjectCheckIn(String projectId, String userId) {
        return allProjectCheckIns(projectId).stream()
                .filter(record -> userId.equals(record.userId()))
                .max(Comparator.comparingLong(ProjectCheckInRecord::createdAt));
    }

    @Override
    public ProjectAcceptanceRecord saveAcceptanceRecord(ProjectAcceptanceRecord record) {
        return toAcceptance(documents.save(ACCEPTANCE, record.id(), acceptanceDocument(record)));
    }

    @Override
    public List<ProjectAcceptanceRecord> listAcceptanceRecords(String detailId, int page, int size) {
        return documents.findByField(ACCEPTANCE, "detailId", detailId, page, size).stream()
                .map(this::toAcceptance)
                .sorted(Comparator.comparingLong(ProjectAcceptanceRecord::createdAt).reversed())
                .toList();
    }

    @Override
    public ProjectProgressEvent saveEvent(ProjectProgressEvent event) {
        return toEvent(documents.save(EVENTS, event.id(), eventDocument(event)));
    }

    @Override
    public List<ProjectProgressEvent> listEvents(String projectId, Long since, int page, int size) {
        return documents.findByField(EVENTS, "projectId", projectId, page, size).stream()
                .map(this::toEvent)
                .filter(event -> since == null || event.createdAt() > since)
                .sorted(Comparator.comparingLong(ProjectProgressEvent::createdAt).reversed())
                .toList();
    }

    private List<ProjectWorkDetail> allDetails() {
        List<ProjectWorkDetail> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<ProjectWorkDetail> batch = documents.findAll(DETAILS, page, SCAN_PAGE_SIZE).stream().map(this::toDetail).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private List<ProjectCheckInRecord> allCheckIns(String detailId) {
        List<ProjectCheckInRecord> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<ProjectCheckInRecord> batch = documents.findByField(CHECK_INS, "detailId", detailId, page, SCAN_PAGE_SIZE)
                    .stream().map(this::toCheckIn).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private List<ProjectCheckInRecord> allProjectCheckIns(String projectId) {
        List<ProjectCheckInRecord> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<ProjectCheckInRecord> batch = documents.findByField(CHECK_INS, "projectId", projectId, page, SCAN_PAGE_SIZE)
                    .stream().map(this::toCheckIn).toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
    }

    private Map<String, Object> projectDocument(ProjectProgressProject project) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", project.id());
        document.put("name", project.name());
        document.put("description", project.description());
        document.put("managerUserIds", project.managerUserIds());
        document.put("memberUserIds", project.memberUserIds());
        document.put("statuses", project.statuses().stream().map(this::statusDocument).toList());
        document.put("defaultStatusCode", project.defaultStatusCode());
        document.put("doneStatusCode", project.doneStatusCode());
        document.put("reworkStatusCode", project.reworkStatusCode());
        document.put("minCheckInIntervalMinutes", project.minCheckInIntervalMinutes());
        document.put("allowedCheckInTypes", project.allowedCheckInTypes().stream().map(Enum::name).toList());
        document.put("minecraftPolicy", minecraftPolicyDocument(project.minecraftPolicy()));
        document.put("enabled", project.enabled());
        document.put("createdAt", project.createdAt());
        document.put("updatedAt", project.updatedAt());
        return document;
    }

    private Map<String, Object> detailDocument(ProjectWorkDetail detail) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", detail.id());
        document.put("projectId", detail.projectId());
        document.put("title", detail.title());
        document.put("description", detail.description());
        document.put("statusCode", detail.statusCode());
        document.put("assignmentMode", detail.assignmentMode().name());
        document.put("requiredAssigneeCount", detail.requiredAssigneeCount());
        document.put("candidateUserIds", detail.candidateUserIds());
        document.put("assigneeUserIds", detail.assigneeUserIds());
        document.put("acceptorUserIds", detail.acceptorUserIds());
        document.put("published", detail.published());
        document.put("pendingAcceptance", detail.pendingAcceptance());
        document.put("dueAt", detail.dueAt());
        document.put("createdAt", detail.createdAt());
        document.put("updatedAt", detail.updatedAt());
        return document;
    }

    private Map<String, Object> checkInDocument(ProjectCheckInRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", record.id());
        document.put("projectId", record.projectId());
        document.put("detailId", record.detailId());
        document.put("userId", record.userId());
        document.put("type", record.type().name());
        document.put("summary", record.summary());
        document.put("files", record.files().stream().map(this::fileDocument).toList());
        document.put("location", locationDocument(record.location()));
        document.put("minecraft", minecraftEvidenceDocument(record.minecraft()));
        document.put("createdAt", record.createdAt());
        return document;
    }

    private Map<String, Object> acceptanceDocument(ProjectAcceptanceRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", record.id());
        document.put("projectId", record.projectId());
        document.put("detailId", record.detailId());
        document.put("operatorUserId", record.operatorUserId());
        document.put("result", record.result().name());
        document.put("fromStatusCode", record.fromStatusCode());
        document.put("toStatusCode", record.toStatusCode());
        document.put("reason", record.reason());
        document.put("createdAt", record.createdAt());
        return document;
    }

    private Map<String, Object> eventDocument(ProjectProgressEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", event.id());
        document.put("projectId", event.projectId());
        document.put("detailId", event.detailId());
        document.put("operatorUserId", event.operatorUserId());
        document.put("type", event.type().name());
        document.put("message", event.message());
        document.put("metadata", event.metadata());
        document.put("createdAt", event.createdAt());
        return document;
    }

    private Map<String, Object> statusDocument(ProjectStatusOption status) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("code", status.code());
        document.put("label", status.label());
        document.put("terminal", status.terminal());
        document.put("sort", status.sort());
        return document;
    }

    private Map<String, Object> minecraftPolicyDocument(ProjectMinecraftPolicy policy) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("enabled", policy.enabled());
        document.put("serverId", policy.serverId());
        document.put("requiredOnlineMinutes", policy.requiredOnlineMinutes());
        document.put("includeAfk", policy.includeAfk());
        document.put("autoCheckInEnabled", policy.autoCheckInEnabled());
        return document;
    }

    private Map<String, Object> fileDocument(ProjectFileEvidence file) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("objectKey", file.objectKey());
        document.put("filename", file.filename());
        document.put("contentType", file.contentType());
        document.put("size", file.size());
        document.put("image", file.image());
        return document;
    }

    private Map<String, Object> locationDocument(ProjectLocationEvidence location) {
        if (location == null) {
            return null;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("address", location.address());
        document.put("latitude", location.latitude());
        document.put("longitude", location.longitude());
        return document;
    }

    private Map<String, Object> minecraftEvidenceDocument(ProjectMinecraftEvidence minecraft) {
        if (minecraft == null) {
            return null;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("serverId", minecraft.serverId());
        document.put("playerId", minecraft.playerId());
        document.put("playerName", minecraft.playerName());
        document.put("totalOnlineMillis", minecraft.totalOnlineMillis());
        document.put("totalAfkMillis", minecraft.totalAfkMillis());
        document.put("effectiveOnlineMillis", minecraft.effectiveOnlineMillis());
        return document;
    }

    private ProjectProgressProject toProject(Map<String, Object> document) {
        return new ProjectProgressProject(
                string(document, "id"),
                string(document, "name"),
                string(document, "description"),
                stringList(document.get("managerUserIds")),
                stringList(document.get("memberUserIds")),
                statusList(document.get("statuses")),
                string(document, "defaultStatusCode"),
                string(document, "doneStatusCode"),
                string(document, "reworkStatusCode"),
                integer(document, "minCheckInIntervalMinutes", 0),
                checkInTypes(document.get("allowedCheckInTypes")),
                toMinecraftPolicy(map(document.get("minecraftPolicy"))),
                bool(document, "enabled", true),
                number(document, "createdAt", 0),
                number(document, "updatedAt", 0)
        );
    }

    private ProjectWorkDetail toDetail(Map<String, Object> document) {
        return new ProjectWorkDetail(
                string(document, "id"),
                string(document, "projectId"),
                string(document, "title"),
                string(document, "description"),
                string(document, "statusCode"),
                ProjectAssignmentMode.of(string(document, "assignmentMode")),
                integer(document, "requiredAssigneeCount", 1),
                stringList(document.get("candidateUserIds")),
                stringList(document.get("assigneeUserIds")),
                stringList(document.get("acceptorUserIds")),
                bool(document, "published", false),
                bool(document, "pendingAcceptance", false),
                longObject(document, "dueAt"),
                number(document, "createdAt", 0),
                number(document, "updatedAt", 0)
        );
    }

    private ProjectCheckInRecord toCheckIn(Map<String, Object> document) {
        return new ProjectCheckInRecord(
                string(document, "id"),
                string(document, "projectId"),
                string(document, "detailId"),
                string(document, "userId"),
                ProjectCheckInType.of(string(document, "type")),
                string(document, "summary"),
                fileList(document.get("files")),
                toLocation(map(document.get("location"))),
                toMinecraftEvidence(map(document.get("minecraft"))),
                number(document, "createdAt", 0)
        );
    }

    private ProjectAcceptanceRecord toAcceptance(Map<String, Object> document) {
        return new ProjectAcceptanceRecord(
                string(document, "id"),
                string(document, "projectId"),
                string(document, "detailId"),
                string(document, "operatorUserId"),
                ProjectAcceptanceResult.valueOf(string(document, "result")),
                string(document, "fromStatusCode"),
                string(document, "toStatusCode"),
                string(document, "reason"),
                number(document, "createdAt", 0)
        );
    }

    private ProjectProgressEvent toEvent(Map<String, Object> document) {
        return new ProjectProgressEvent(
                string(document, "id"),
                string(document, "projectId"),
                string(document, "detailId"),
                string(document, "operatorUserId"),
                ProjectProgressEventType.valueOf(string(document, "type")),
                string(document, "message"),
                map(document.get("metadata")),
                number(document, "createdAt", 0)
        );
    }

    private List<ProjectStatusOption> statusList(Object value) {
        List<Map<String, Object>> rows = mapList(value);
        if (rows.isEmpty()) {
            return ProjectProgressProject.defaultStatuses();
        }
        return rows.stream()
                .map(row -> new ProjectStatusOption(string(row, "code"), string(row, "label"),
                        bool(row, "terminal", false), integer(row, "sort", 0)))
                .toList();
    }

    private ProjectMinecraftPolicy toMinecraftPolicy(Map<String, Object> document) {
        if (document == null || document.isEmpty()) {
            return ProjectMinecraftPolicy.disabled();
        }
        return new ProjectMinecraftPolicy(bool(document, "enabled", false), string(document, "serverId"),
                integer(document, "requiredOnlineMinutes", 0), bool(document, "includeAfk", false),
                bool(document, "autoCheckInEnabled", false));
    }

    private ProjectLocationEvidence toLocation(Map<String, Object> document) {
        return document == null || document.isEmpty() ? null : new ProjectLocationEvidence(string(document, "address"),
                doubleObject(document, "latitude"), doubleObject(document, "longitude"));
    }

    private ProjectMinecraftEvidence toMinecraftEvidence(Map<String, Object> document) {
        return document == null || document.isEmpty() ? null : new ProjectMinecraftEvidence(string(document, "serverId"),
                string(document, "playerId"), string(document, "playerName"),
                number(document, "totalOnlineMillis", 0), number(document, "totalAfkMillis", 0),
                number(document, "effectiveOnlineMillis", 0));
    }

    private List<ProjectFileEvidence> fileList(Object value) {
        return mapList(value).stream()
                .map(row -> new ProjectFileEvidence(string(row, "objectKey"), string(row, "filename"),
                        string(row, "contentType"), number(row, "size", 0), bool(row, "image", false)))
                .toList();
    }

    private List<ProjectCheckInType> checkInTypes(Object value) {
        List<String> values = stringList(value);
        return values.isEmpty() ? List.of(ProjectCheckInType.IMAGE, ProjectCheckInType.FILE, ProjectCheckInType.LOCATION)
                : values.stream().map(ProjectCheckInType::of).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> row)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(this::map)
                .toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        return rows.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> String.valueOf(item).trim())
                .toList();
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Map<String, Object> document, String key, int defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private long number(Map<String, Object> document, String key, long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private Long longObject(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
    }

    private Double doubleObject(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null || String.valueOf(value).isBlank() ? null : Double.parseDouble(String.valueOf(value));
    }

    private boolean bool(Map<String, Object> document, String key, boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }
}
