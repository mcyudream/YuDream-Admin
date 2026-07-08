package online.yudream.base.plugin.activityproof.application.service;

import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofExportCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofMappingSaveCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofSettingsSaveCmd;
import online.yudream.base.plugin.activityproof.application.cmd.ActivityProofTemplateUploadCmd;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofDependencyDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofExportDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofMappingDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofParticipantDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofServerDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofSettingsDTO;
import online.yudream.base.plugin.activityproof.application.dto.ActivityProofStatusDTO;
import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofExportRecord;
import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofSettings;
import online.yudream.base.plugin.activityproof.domain.aggregate.PlayerStudentMapping;
import online.yudream.base.plugin.activityproof.domain.repo.ActivityProofRepository;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.document.PluginRenderedDocument;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftPlayerActivity;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftServer;
import online.yudream.base.plugin.spi.system.minecraft.PluginMinecraftService;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import online.yudream.base.plugin.spi.system.studentinfo.PluginStudentInfoProfile;
import online.yudream.base.plugin.spi.system.studentinfo.PluginStudentInfoService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActivityProofAppService {

    private static final String MINECRAFT_PLUGIN = "minecraft-server";
    private static final String STUDENT_INFO_PLUGIN = "yudream-student-info";
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int MAX_SCAN_SIZE = 1000;

    private final ActivityProofRepository repository;
    private final PluginFileStore files;
    private final FrameworkServices framework;

    public ActivityProofAppService(ActivityProofRepository repository, PluginFileStore files, FrameworkServices framework) {
        this.repository = repository;
        this.files = files;
        this.framework = framework;
    }

    public ActivityProofStatusDTO status() {
        return new ActivityProofStatusDTO(dependencies(), toDTO(repository.settings()));
    }

    public ActivityProofDependencyDTO dependencies() {
        return new ActivityProofDependencyDTO(
                minecraftService().isPresent(),
                studentInfoService().isPresent(),
                wordTemplateEnabled()
        );
    }

    public ActivityProofSettingsDTO settings() {
        return toDTO(repository.settings());
    }

    public ActivityProofSettingsDTO saveSettings(ActivityProofSettingsSaveCmd cmd) {
        ActivityProofSettings settings = repository.settings()
                .withDefaults(cmd.defaultActivityName(), cmd.defaultCollege(), cmd.defaultIssuer(), System.currentTimeMillis());
        return toDTO(repository.saveSettings(settings));
    }

    public ActivityProofSettingsDTO uploadTemplate(ActivityProofTemplateUploadCmd cmd) {
        String filename = requireDocxFilename(cmd.filename());
        byte[] content = decodeBase64(cmd.contentBase64(), "模板文件不能为空");
        String objectKey = files.put("templates/activity-proof-template.docx",
                new ByteArrayInputStream(content), content.length, contentType(cmd.contentType()));
        ActivityProofSettings settings = repository.settings().withTemplate(objectKey, filename, System.currentTimeMillis());
        return toDTO(repository.saveSettings(settings));
    }

    public List<ActivityProofServerDTO> servers() {
        return minecraft().minecraftServers(true).stream()
                .map(server -> new ActivityProofServerDTO(server.id(), server.name(), server.enabled(),
                        server.currentSeasonName(), server.currentSeasonStartedAt()))
                .toList();
    }

    public List<ActivityProofMappingDTO> mappings(String serverId, int page, int size) {
        return repository.mappings(serverId, safePage(page), safeSize(size)).stream()
                .map(this::toDTO)
                .toList();
    }

    public ActivityProofMappingDTO saveMapping(ActivityProofMappingSaveCmd cmd) {
        String serverId = requireText(cmd.serverId(), "服务器不能为空");
        String playerId = requireText(cmd.playerId(), "玩家 ID 不能为空");
        requireText(cmd.studentNo(), "学号不能为空");
        PlayerStudentMapping mapping = repository.mapping(serverId, playerId)
                .map(existing -> existing.update(cmd.playerName(), cmd.studentNo()))
                .orElseGet(() -> PlayerStudentMapping.create(serverId, playerId, cmd.playerName(), cmd.studentNo()));
        return toDTO(repository.saveMapping(mapping));
    }

    public void deleteMapping(String id) {
        repository.deleteMapping(requireText(id, "映射 ID 不能为空"));
    }

    public List<ActivityProofParticipantDTO> participants(String serverId, Integer minOnlineMinutes, Boolean includeAfk) {
        return buildParticipants(requireText(serverId, "服务器不能为空"), minOnlineMinutes, includeAfk, List.of());
    }

    public ActivityProofExportDTO export(ActivityProofExportCmd cmd, String operatorUserId) {
        ActivityProofSettings settings = repository.settings();
        if (!settings.hasTemplate()) {
            throw new IllegalArgumentException("请先上传 Word 模板");
        }
        if (!wordTemplateEnabled()) {
            throw new IllegalArgumentException("Word 模板能力未启用，请先在能力管理中启用 document-template");
        }
        String serverId = requireText(cmd.serverId(), "服务器不能为空");
        PluginMinecraftServer server = minecraft().minecraftServer(serverId)
                .orElseThrow(() -> new IllegalArgumentException("服务器不存在：" + serverId));
        List<ActivityProofParticipantDTO> participants = buildParticipants(serverId, cmd.minOnlineMinutes(), cmd.includeAfk(), cmd.selectedPlayerIds());
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的玩家记录");
        }
        byte[] templateContent = readFile(settings.templateObjectKey());
        Map<String, Object> data = buildTemplateData(cmd, server, settings, participants);
        PluginRenderedDocument rendered = framework.wordTemplates().render(templateContent, data);
        String recordId = UUID.randomUUID().toString();
        String filename = outputFilename(cmd.activityName(), recordId);
        String objectKey = files.put("exports/" + recordId + ".docx",
                new ByteArrayInputStream(rendered.content()), rendered.content().length,
                rendered.contentType() == null ? DOCX_CONTENT_TYPE : rendered.contentType());
        long unmatchedCount = participants.stream().filter(item -> !item.matched()).count();
        ActivityProofExportRecord record = ActivityProofExportRecord.create(server.id(), server.name(), text(cmd.activityName()),
                objectKey, filename, participants.size(), (int) unmatchedCount, operatorUserId);
        return toDTO(repository.saveExportRecord(record));
    }

    public List<ActivityProofExportDTO> exportRecords(int page, int size) {
        return repository.exportRecords(safePage(page), safeSize(size)).stream()
                .map(this::toDTO)
                .toList();
    }

    public PluginStoredFile download(String id) {
        ActivityProofExportRecord record = repository.exportRecord(requireText(id, "导出记录不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("导出记录不存在"));
        return files.get(record.outputObjectKey());
    }

    private List<ActivityProofParticipantDTO> buildParticipants(String serverId, Integer minOnlineMinutes,
                                                                Boolean includeAfk, List<String> selectedPlayerIds) {
        Set<String> selected = selectedPlayerIds == null ? Set.of() : selectedPlayerIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, PlayerStudentMapping> mappings = repository.mappings(serverId, 1, MAX_SCAN_SIZE).stream()
                .collect(Collectors.toMap(PlayerStudentMapping::playerId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        List<PluginStudentInfoProfile> students = studentInfoService()
                .map(service -> service.studentInfos(null, 1, MAX_SCAN_SIZE))
                .orElseGet(List::of);
        Map<String, PluginStudentInfoProfile> studentsByNo = students.stream()
                .filter(item -> item.studentNo() != null && !item.studentNo().isBlank())
                .collect(Collectors.toMap(PluginStudentInfoProfile::studentNo, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        Map<String, PluginStudentInfoProfile> studentsByName = students.stream()
                .filter(item -> item.studentName() != null && !item.studentName().isBlank())
                .collect(Collectors.toMap(PluginStudentInfoProfile::studentName, Function.identity(), (first, second) -> first, LinkedHashMap::new));

        long minMillis = Math.max(minOnlineMinutes == null ? 0 : minOnlineMinutes, 0) * 60_000L;
        List<ActivityProofParticipantDTO> result = new ArrayList<>();
        int index = 1;
        for (PluginMinecraftPlayerActivity activity : minecraft().minecraftPlayerActivities(serverId, 1, MAX_SCAN_SIZE)) {
            if (!selected.isEmpty() && !selected.contains(activity.playerId())) {
                continue;
            }
            long effectiveMillis = effectiveMillis(activity, includeAfk);
            if (effectiveMillis < minMillis) {
                continue;
            }
            PlayerStudentMapping mapping = mappings.get(activity.playerId());
            PluginStudentInfoProfile profile = resolveStudent(activity, mapping, studentsByNo, studentsByName);
            result.add(toParticipant(index++, activity, mapping, profile, effectiveMillis));
        }
        List<ActivityProofParticipantDTO> sorted = result.stream()
                .sorted(Comparator.comparing(ActivityProofParticipantDTO::matched).reversed()
                        .thenComparing(ActivityProofParticipantDTO::studentNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ActivityProofParticipantDTO::playerName, Comparator.nullsLast(String::compareTo)))
                .toList();
        List<ActivityProofParticipantDTO> reindexed = new ArrayList<>();
        for (int cursor = 0; cursor < sorted.size(); cursor++) {
            reindexed.add(withIndex(sorted.get(cursor), cursor + 1));
        }
        return reindexed;
    }

    private ActivityProofParticipantDTO withIndex(ActivityProofParticipantDTO item, int index) {
        return new ActivityProofParticipantDTO(
                index,
                item.serverId(),
                item.playerId(),
                item.playerName(),
                item.studentName(),
                item.studentNo(),
                item.className(),
                item.college(),
                item.matched(),
                item.mapped(),
                item.totalOnlineMillis(),
                item.totalAfkMillis(),
                item.effectiveOnlineMillis()
        );
    }

    private PluginStudentInfoProfile resolveStudent(PluginMinecraftPlayerActivity activity,
                                                    PlayerStudentMapping mapping,
                                                    Map<String, PluginStudentInfoProfile> studentsByNo,
                                                    Map<String, PluginStudentInfoProfile> studentsByName) {
        if (mapping != null) {
            PluginStudentInfoProfile profile = studentsByNo.get(mapping.studentNo());
            if (profile != null) {
                return profile;
            }
            return studentInfoService().flatMap(service -> service.findStudentInfoByStudentNo(mapping.studentNo())).orElse(null);
        }
        PluginStudentInfoProfile byPlayerId = studentsByNo.get(activity.playerId());
        if (byPlayerId != null) {
            return byPlayerId;
        }
        return studentsByName.get(activity.playerName());
    }

    private ActivityProofParticipantDTO toParticipant(int index, PluginMinecraftPlayerActivity activity,
                                                      PlayerStudentMapping mapping, PluginStudentInfoProfile profile,
                                                      long effectiveMillis) {
        boolean matched = profile != null;
        return new ActivityProofParticipantDTO(
                index,
                activity.serverId(),
                activity.playerId(),
                activity.playerName(),
                matched ? profile.studentName() : activity.playerName(),
                matched ? profile.studentNo() : mapping == null ? "" : mapping.studentNo(),
                matched ? profile.className() : "",
                matched ? profile.college() : "",
                matched,
                mapping != null,
                activity.totalOnlineMillis(),
                activity.totalAfkMillis(),
                effectiveMillis
        );
    }

    private Map<String, Object> buildTemplateData(ActivityProofExportCmd cmd, PluginMinecraftServer server,
                                                  ActivityProofSettings settings, List<ActivityProofParticipantDTO> participants) {
        String activityName = firstText(cmd.activityName(), settings.defaultActivityName(), "Minecraft 星空社活动");
        String college = firstText(cmd.college(), settings.defaultCollege(), "计算机科学与技术学院");
        String issueDate = firstText(cmd.issueDate(), todayText());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("participantTableAppend", true);
        data.put("proofNo", firstText(cmd.proofNo(), defaultProofNo()));
        data.put("activityName", activityName);
        data.put("activityDate", firstText(cmd.activityDate(), issueDate));
        data.put("college", college);
        data.put("collegeName", college);
        data.put("issuer", firstText(cmd.issuer(), settings.defaultIssuer()));
        data.put("issueDate", issueDate);
        data.put("serverId", server.id());
        data.put("serverName", server.name());
        data.put("currentSeasonName", server.currentSeasonName());
        data.put("participantCount", participants.size());
        data.put("participants", participants.stream().map(ActivityProofParticipantDTO::templateData).toList());
        return data;
    }

    private long effectiveMillis(PluginMinecraftPlayerActivity activity, Boolean includeAfk) {
        if (Boolean.TRUE.equals(includeAfk)) {
            return activity.totalOnlineMillis();
        }
        return Math.max(0, activity.totalOnlineMillis() - activity.totalAfkMillis());
    }

    private PluginMinecraftService minecraft() {
        return minecraftService().orElseThrow(() -> new IllegalArgumentException("Minecraft 服务器插件未启用"));
    }

    private Optional<PluginMinecraftService> minecraftService() {
        return framework == null ? Optional.empty() : framework.extension(MINECRAFT_PLUGIN, PluginMinecraftService.class);
    }

    private Optional<PluginStudentInfoService> studentInfoService() {
        return framework == null ? Optional.empty() : framework.extension(STUDENT_INFO_PLUGIN, PluginStudentInfoService.class);
    }

    private boolean wordTemplateEnabled() {
        return framework != null && framework.wordTemplates() != null && framework.wordTemplates().enabled();
    }

    private byte[] readFile(String objectKey) {
        try (var inputStream = files.get(objectKey).inputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + e.getMessage(), e);
        }
    }

    private ActivityProofSettingsDTO toDTO(ActivityProofSettings settings) {
        return new ActivityProofSettingsDTO(settings.hasTemplate(), settings.templateFilename(), settings.templateUpdatedAt(),
                settings.defaultActivityName(), settings.defaultCollege(), settings.defaultIssuer(), settings.updatedAt());
    }

    private ActivityProofMappingDTO toDTO(PlayerStudentMapping mapping) {
        return new ActivityProofMappingDTO(mapping.id(), mapping.serverId(), mapping.playerId(), mapping.playerName(),
                mapping.studentNo(), mapping.createdAt(), mapping.updatedAt());
    }

    private ActivityProofExportDTO toDTO(ActivityProofExportRecord record) {
        return new ActivityProofExportDTO(record.id(), record.serverId(), record.serverName(), record.activityName(),
                record.outputFilename(), "/exports/" + encode(record.id()) + "/download",
                record.participantCount(), record.unmatchedCount(), record.operatorUserId(), record.generatedAt());
    }

    private String requireDocxFilename(String filename) {
        String value = requireText(filename, "模板文件名不能为空");
        if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("模板文件必须是 .docx 格式");
        }
        return value;
    }

    private byte[] decodeBase64(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String content = value.contains(",") ? value.substring(value.indexOf(',') + 1) : value;
        return Base64.getDecoder().decode(content);
    }

    private String contentType(String value) {
        return value == null || value.isBlank() ? DOCX_CONTENT_TYPE : value.trim();
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.max(Math.min(size <= 0 ? 20 : size, MAX_SCAN_SIZE), 1);
    }

    private String defaultProofNo() {
        return "NO." + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault()).format(Instant.now());
    }

    private String todayText() {
        return DateTimeFormatter.ofPattern("yyyy年M月d日").format(LocalDate.now());
    }

    private String outputFilename(String activityName, String recordId) {
        String baseName = text(activityName).isBlank() ? "minecraft-activity-proof" : text(activityName);
        return baseName.replaceAll("[\\\\/:*?\"<>|]", "_") + "-" + recordId.substring(0, 8) + ".docx";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
