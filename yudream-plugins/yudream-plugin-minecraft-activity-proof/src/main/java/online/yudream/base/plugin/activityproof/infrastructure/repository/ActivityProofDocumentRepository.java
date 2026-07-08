package online.yudream.base.plugin.activityproof.infrastructure.repository;

import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofExportRecord;
import online.yudream.base.plugin.activityproof.domain.aggregate.ActivityProofSettings;
import online.yudream.base.plugin.activityproof.domain.aggregate.PlayerStudentMapping;
import online.yudream.base.plugin.activityproof.domain.repo.ActivityProofRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActivityProofDocumentRepository implements ActivityProofRepository {

    private static final String SETTINGS = "settings";
    private static final String MAPPINGS = "mappings";
    private static final String EXPORTS = "exports";

    private final PluginDocumentStore documents;

    public ActivityProofDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public ActivityProofSettings settings() {
        return documents.findById(SETTINGS, ActivityProofSettings.ID)
                .map(this::toSettings)
                .orElseGet(ActivityProofSettings::empty);
    }

    @Override
    public ActivityProofSettings saveSettings(ActivityProofSettings settings) {
        return toSettings(documents.save(SETTINGS, ActivityProofSettings.ID, settingsDocument(settings)));
    }

    @Override
    public Optional<PlayerStudentMapping> mapping(String serverId, String playerId) {
        return documents.findById(MAPPINGS, PlayerStudentMapping.id(serverId, playerId)).map(this::toMapping);
    }

    @Override
    public List<PlayerStudentMapping> mappings(String serverId, int page, int size) {
        List<Map<String, Object>> rows = serverId == null || serverId.isBlank()
                ? documents.findAll(MAPPINGS, page, size)
                : documents.findByField(MAPPINGS, "serverId", serverId, page, size);
        return rows.stream().map(this::toMapping).toList();
    }

    @Override
    public PlayerStudentMapping saveMapping(PlayerStudentMapping mapping) {
        return toMapping(documents.save(MAPPINGS, mapping.id(), mappingDocument(mapping)));
    }

    @Override
    public void deleteMapping(String id) {
        documents.delete(MAPPINGS, id);
    }

    @Override
    public ActivityProofExportRecord saveExportRecord(ActivityProofExportRecord record) {
        return toExport(documents.save(EXPORTS, record.id(), exportDocument(record)));
    }

    @Override
    public Optional<ActivityProofExportRecord> exportRecord(String id) {
        return documents.findById(EXPORTS, id).map(this::toExport);
    }

    @Override
    public List<ActivityProofExportRecord> exportRecords(int page, int size) {
        return documents.findAll(EXPORTS, page, size).stream()
                .map(this::toExport)
                .sorted(java.util.Comparator.comparingLong(ActivityProofExportRecord::generatedAt).reversed())
                .toList();
    }

    private Map<String, Object> settingsDocument(ActivityProofSettings settings) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", ActivityProofSettings.ID);
        document.put("templateObjectKey", settings.templateObjectKey());
        document.put("templateFilename", settings.templateFilename());
        document.put("templateUpdatedAt", settings.templateUpdatedAt());
        document.put("defaultActivityName", settings.defaultActivityName());
        document.put("defaultCollege", settings.defaultCollege());
        document.put("defaultIssuer", settings.defaultIssuer());
        document.put("updatedAt", settings.updatedAt());
        return document;
    }

    private Map<String, Object> mappingDocument(PlayerStudentMapping mapping) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", mapping.id());
        document.put("serverId", mapping.serverId());
        document.put("playerId", mapping.playerId());
        document.put("playerName", mapping.playerName());
        document.put("studentNo", mapping.studentNo());
        document.put("createdAt", mapping.createdAt());
        document.put("updatedAt", mapping.updatedAt());
        return document;
    }

    private Map<String, Object> exportDocument(ActivityProofExportRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", record.id());
        document.put("serverId", record.serverId());
        document.put("serverName", record.serverName());
        document.put("activityName", record.activityName());
        document.put("outputObjectKey", record.outputObjectKey());
        document.put("outputFilename", record.outputFilename());
        document.put("participantCount", record.participantCount());
        document.put("unmatchedCount", record.unmatchedCount());
        document.put("operatorUserId", record.operatorUserId());
        document.put("generatedAt", record.generatedAt());
        return document;
    }

    private ActivityProofSettings toSettings(Map<String, Object> document) {
        return new ActivityProofSettings(
                string(document, "id"),
                string(document, "templateObjectKey"),
                string(document, "templateFilename"),
                number(document, "templateUpdatedAt", 0),
                string(document, "defaultActivityName"),
                string(document, "defaultCollege"),
                string(document, "defaultIssuer"),
                number(document, "updatedAt", 0)
        );
    }

    private PlayerStudentMapping toMapping(Map<String, Object> document) {
        return new PlayerStudentMapping(
                string(document, "id"),
                string(document, "serverId"),
                string(document, "playerId"),
                string(document, "playerName"),
                string(document, "studentNo"),
                number(document, "createdAt", 0),
                number(document, "updatedAt", 0)
        );
    }

    private ActivityProofExportRecord toExport(Map<String, Object> document) {
        return new ActivityProofExportRecord(
                string(document, "id"),
                string(document, "serverId"),
                string(document, "serverName"),
                string(document, "activityName"),
                string(document, "outputObjectKey"),
                string(document, "outputFilename"),
                integer(document, "participantCount", 0),
                integer(document, "unmatchedCount", 0),
                string(document, "operatorUserId"),
                number(document, "generatedAt", 0)
        );
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long number(Map<String, Object> document, String key, long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private int integer(Map<String, Object> document, String key, int defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Integer.parseInt(String.valueOf(value));
    }
}
