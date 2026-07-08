package online.yudream.base.plugin.minecraft.infrastructure.repository;

import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftSeasonOperation;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftServer;
import online.yudream.base.plugin.minecraft.domain.aggregate.MinecraftPlayerActivity;
import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftEdition;
import online.yudream.base.plugin.minecraft.domain.enumerate.MinecraftSeasonOperationStatus;
import online.yudream.base.plugin.minecraft.domain.repo.MinecraftServerRepository;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftEndpointStatus;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftInheritanceRule;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftSeasonAdjustment;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerEndpoint;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerSeason;
import online.yudream.base.plugin.minecraft.domain.valobj.MinecraftServerStatus;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MinecraftServerDocumentRepository implements MinecraftServerRepository {

    private static final String SERVERS = "servers";
    private static final String STATUSES = "statuses";
    private static final String OPERATIONS = "season-operations";
    private static final String PLAYER_ACTIVITIES = "player-activities";

    private final PluginDocumentStore documents;

    public MinecraftServerDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public MinecraftServer save(MinecraftServer server) {
        return toServer(documents.save(SERVERS, server.id(), serverDocument(server)));
    }

    @Override
    public Optional<MinecraftServer> findById(String id) {
        return documents.findById(SERVERS, id).map(this::toServer);
    }

    @Override
    public List<MinecraftServer> list(int page, int size, boolean includeDisabled) {
        return documents.findAll(SERVERS, page, size).stream()
                .map(this::toServer)
                .filter(server -> includeDisabled || server.enabled())
                .sorted(java.util.Comparator.comparingInt(MinecraftServer::sort).thenComparing(MinecraftServer::name))
                .toList();
    }

    @Override
    public long count(boolean includeDisabled) {
        if (includeDisabled) {
            return documents.count(SERVERS);
        }
        return list(1, 1000, false).size();
    }

    @Override
    public void delete(String id) {
        documents.delete(SERVERS, id);
    }

    @Override
    public MinecraftServerStatus saveStatus(MinecraftServerStatus status) {
        return toStatus(documents.save(STATUSES, status.serverId(), statusDocument(status)));
    }

    @Override
    public Optional<MinecraftServerStatus> findStatus(String serverId) {
        return documents.findById(STATUSES, serverId).map(this::toStatus);
    }

    @Override
    public MinecraftSeasonOperation saveOperation(MinecraftSeasonOperation operation) {
        return toOperation(documents.save(OPERATIONS, operation.id(), operationDocument(operation)));
    }

    @Override
    public Optional<MinecraftSeasonOperation> findOperation(String operationId) {
        return documents.findById(OPERATIONS, operationId).map(this::toOperation);
    }

    @Override
    public List<MinecraftSeasonOperation> listOperations(String serverId, int page, int size) {
        List<Map<String, Object>> rows = serverId == null || serverId.isBlank()
                ? documents.findAll(OPERATIONS, page, size)
                : documents.findByField(OPERATIONS, "serverId", serverId, page, size);
        return rows.stream()
                .map(this::toOperation)
                .sorted(java.util.Comparator.comparingLong(MinecraftSeasonOperation::createdAt).reversed())
                .toList();
    }

    @Override
    public MinecraftPlayerActivity savePlayerActivity(MinecraftPlayerActivity activity) {
        return toPlayerActivity(documents.save(PLAYER_ACTIVITIES, activity.id(), playerActivityDocument(activity)));
    }

    @Override
    public Optional<MinecraftPlayerActivity> findPlayerActivity(String serverId, String playerId) {
        return documents.findById(PLAYER_ACTIVITIES, MinecraftPlayerActivity.id(serverId, playerId)).map(this::toPlayerActivity);
    }

    @Override
    public List<MinecraftPlayerActivity> listPlayerActivities(String serverId, int page, int size) {
        return documents.findByField(PLAYER_ACTIVITIES, "serverId", serverId, page, size).stream()
                .map(this::toPlayerActivity)
                .sorted(java.util.Comparator.comparing(MinecraftPlayerActivity::online).reversed()
                        .thenComparing(java.util.Comparator.comparingLong(MinecraftPlayerActivity::updatedAt).reversed()))
                .toList();
    }

    private Map<String, Object> serverDocument(MinecraftServer server) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", server.id());
        document.put("name", server.name());
        document.put("descriptionMarkdown", server.descriptionMarkdown());
        document.put("enabled", server.enabled());
        document.put("sort", server.sort());
        document.put("endpoints", server.endpoints().stream().map(this::endpointDocument).toList());
        document.put("seasons", server.seasons().stream().map(this::seasonDocument).toList());
        document.put("createdAt", server.createdAt());
        document.put("updatedAt", server.updatedAt());
        return document;
    }

    private Map<String, Object> endpointDocument(MinecraftServerEndpoint endpoint) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", endpoint.id());
        document.put("name", endpoint.name());
        document.put("host", endpoint.host());
        document.put("port", endpoint.port());
        document.put("edition", endpoint.edition().name());
        document.put("primaryLine", endpoint.primaryLine());
        document.put("enabled", endpoint.enabled());
        document.put("sort", endpoint.sort());
        return document;
    }

    private Map<String, Object> seasonDocument(MinecraftServerSeason season) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", season.id());
        document.put("name", season.name());
        document.put("description", season.description());
        document.put("startedAt", season.startedAt());
        document.put("endedAt", season.endedAt());
        document.put("current", season.current());
        document.put("sort", season.sort());
        return document;
    }

    private Map<String, Object> statusDocument(MinecraftServerStatus status) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("serverId", status.serverId());
        document.put("status", status.status());
        document.put("onlinePlayers", status.onlinePlayers());
        document.put("maxPlayers", status.maxPlayers());
        document.put("endpoints", status.endpoints().stream().map(this::endpointStatusDocument).toList());
        document.put("checkedAt", status.checkedAt());
        return document;
    }

    private Map<String, Object> endpointStatusDocument(MinecraftEndpointStatus status) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("endpointId", status.endpointId());
        document.put("status", status.status());
        document.put("onlinePlayers", status.onlinePlayers());
        document.put("maxPlayers", status.maxPlayers());
        document.put("versionName", status.versionName());
        document.put("protocolId", status.protocolId());
        document.put("ping", status.ping());
        document.put("motd", status.motd());
        document.put("errorMessage", status.errorMessage());
        document.put("checkedAt", status.checkedAt());
        return document;
    }

    private Map<String, Object> operationDocument(MinecraftSeasonOperation operation) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", operation.id());
        document.put("serverId", operation.serverId());
        document.put("fromSeasonId", operation.fromSeasonId());
        document.put("toSeasonId", operation.toSeasonId());
        document.put("toSeasonName", operation.toSeasonName());
        document.put("status", operation.status().name());
        document.put("rules", operation.rules().stream().map(this::ruleDocument).toList());
        document.put("adjustments", operation.adjustments().stream().map(this::adjustmentDocument).toList());
        document.put("operatorUserId", operation.operatorUserId());
        document.put("remark", operation.remark());
        document.put("createdAt", operation.createdAt());
        document.put("rolledBackAt", operation.rolledBackAt());
        return document;
    }

    private Map<String, Object> playerActivityDocument(MinecraftPlayerActivity activity) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", activity.id());
        document.put("serverId", activity.serverId());
        document.put("playerId", activity.playerId());
        document.put("playerName", activity.playerName());
        document.put("totalOnlineMillis", activity.totalOnlineMillis());
        document.put("totalAfkMillis", activity.totalAfkMillis());
        document.put("currentOnlineSince", activity.currentOnlineSince());
        document.put("currentAfkSince", activity.currentAfkSince());
        document.put("lastJoinedAt", activity.lastJoinedAt());
        document.put("lastQuitAt", activity.lastQuitAt());
        document.put("createdAt", activity.createdAt());
        document.put("updatedAt", activity.updatedAt());
        return document;
    }

    private Map<String, Object> ruleDocument(MinecraftInheritanceRule rule) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("assetPattern", rule.assetPattern());
        document.put("minAmount", decimalString(rule.minAmount()));
        document.put("maxAmount", decimalString(rule.maxAmount()));
        document.put("inheritRate", decimalString(rule.inheritRate()));
        return document;
    }

    private Map<String, Object> adjustmentDocument(MinecraftSeasonAdjustment adjustment) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userId", adjustment.userId());
        document.put("assetCode", adjustment.assetCode());
        document.put("inheritedAmount", decimalString(adjustment.inheritedAmount()));
        document.put("seasonIncomeAmount", decimalString(adjustment.seasonIncomeAmount()));
        document.put("seasonTotalAmount", decimalString(adjustment.seasonTotalAmount()));
        document.put("nextInheritedAmount", decimalString(adjustment.nextInheritedAmount()));
        document.put("walletBalanceBefore", decimalString(adjustment.walletBalanceBefore()));
        document.put("deltaAmount", decimalString(adjustment.deltaAmount()));
        document.put("direction", adjustment.direction());
        document.put("ruleLabel", adjustment.ruleLabel());
        document.put("walletTransactionId", adjustment.walletTransactionId());
        document.put("rollbackTransactionId", adjustment.rollbackTransactionId());
        return document;
    }

    @SuppressWarnings("unchecked")
    private MinecraftServer toServer(Map<String, Object> document) {
        List<MinecraftServerEndpoint> endpoints = list(document, "endpoints").stream()
                .map(item -> toEndpoint((Map<String, Object>) item))
                .toList();
        List<MinecraftServerSeason> seasons = list(document, "seasons").stream()
                .map(item -> toSeason((Map<String, Object>) item))
                .toList();
        return new MinecraftServer(
                string(document, "id"),
                string(document, "name"),
                string(document, "descriptionMarkdown"),
                bool(document, "enabled", true),
                integer(document, "sort", 0),
                endpoints,
                seasons,
                number(document, "createdAt", 0L),
                number(document, "updatedAt", 0L)
        );
    }

    private MinecraftServerEndpoint toEndpoint(Map<String, Object> document) {
        return new MinecraftServerEndpoint(
                string(document, "id"),
                string(document, "name"),
                string(document, "host"),
                integer(document, "port", 0),
                MinecraftEdition.of(string(document, "edition")),
                bool(document, "primaryLine", false),
                bool(document, "enabled", true),
                integer(document, "sort", 0)
        );
    }

    private MinecraftServerSeason toSeason(Map<String, Object> document) {
        return new MinecraftServerSeason(
                string(document, "id"),
                string(document, "name"),
                string(document, "description"),
                nullableNumber(document, "startedAt"),
                nullableNumber(document, "endedAt"),
                bool(document, "current", false),
                integer(document, "sort", 0)
        );
    }

    @SuppressWarnings("unchecked")
    private MinecraftServerStatus toStatus(Map<String, Object> document) {
        List<MinecraftEndpointStatus> endpoints = list(document, "endpoints").stream()
                .map(item -> toEndpointStatus((Map<String, Object>) item))
                .toList();
        return new MinecraftServerStatus(
                string(document, "serverId"),
                string(document, "status"),
                integer(document, "onlinePlayers", 0),
                integer(document, "maxPlayers", 0),
                endpoints,
                number(document, "checkedAt", 0L)
        );
    }

    private MinecraftEndpointStatus toEndpointStatus(Map<String, Object> document) {
        return new MinecraftEndpointStatus(
                string(document, "endpointId"),
                string(document, "status"),
                integer(document, "onlinePlayers", 0),
                integer(document, "maxPlayers", 0),
                string(document, "versionName"),
                nullableInteger(document, "protocolId"),
                nullableNumber(document, "ping"),
                string(document, "motd"),
                string(document, "errorMessage"),
                number(document, "checkedAt", 0L)
        );
    }

    @SuppressWarnings("unchecked")
    private MinecraftSeasonOperation toOperation(Map<String, Object> document) {
        List<MinecraftInheritanceRule> rules = list(document, "rules").stream()
                .map(item -> toRule((Map<String, Object>) item))
                .toList();
        List<MinecraftSeasonAdjustment> adjustments = list(document, "adjustments").stream()
                .map(item -> toAdjustment((Map<String, Object>) item))
                .toList();
        return new MinecraftSeasonOperation(
                string(document, "id"),
                string(document, "serverId"),
                string(document, "fromSeasonId"),
                string(document, "toSeasonId"),
                string(document, "toSeasonName"),
                MinecraftSeasonOperationStatus.valueOf(string(document, "status")),
                rules,
                adjustments,
                string(document, "operatorUserId"),
                string(document, "remark"),
                number(document, "createdAt", 0L),
                nullableNumber(document, "rolledBackAt")
        );
    }

    private MinecraftInheritanceRule toRule(Map<String, Object> document) {
        return new MinecraftInheritanceRule(
                string(document, "assetPattern"),
                decimal(document, "minAmount", BigDecimal.ZERO),
                decimal(document, "maxAmount", null),
                decimal(document, "inheritRate", BigDecimal.ZERO)
        );
    }

    private MinecraftSeasonAdjustment toAdjustment(Map<String, Object> document) {
        return new MinecraftSeasonAdjustment(
                string(document, "userId"),
                string(document, "assetCode"),
                decimal(document, "inheritedAmount", BigDecimal.ZERO),
                decimal(document, "seasonIncomeAmount", BigDecimal.ZERO),
                decimal(document, "seasonTotalAmount", BigDecimal.ZERO),
                decimal(document, "nextInheritedAmount", BigDecimal.ZERO),
                decimal(document, "walletBalanceBefore", BigDecimal.ZERO),
                decimal(document, "deltaAmount", BigDecimal.ZERO),
                string(document, "direction"),
                string(document, "ruleLabel"),
                string(document, "walletTransactionId"),
                string(document, "rollbackTransactionId")
        );
    }

    private MinecraftPlayerActivity toPlayerActivity(Map<String, Object> document) {
        return new MinecraftPlayerActivity(
                string(document, "id"),
                string(document, "serverId"),
                string(document, "playerId"),
                string(document, "playerName"),
                number(document, "totalOnlineMillis", 0L),
                number(document, "totalAfkMillis", 0L),
                nullableNumber(document, "currentOnlineSince"),
                nullableNumber(document, "currentAfkSince"),
                nullableNumber(document, "lastJoinedAt"),
                nullableNumber(document, "lastQuitAt"),
                number(document, "createdAt", 0L),
                number(document, "updatedAt", 0L)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value instanceof List<?> items
                ? items.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private String decimalString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Map<String, Object> document, String key, Integer defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private Integer nullableInteger(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private Long number(Map<String, Object> document, String key, Long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }

    private Long nullableNumber(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private Boolean bool(Map<String, Object> document, String key, Boolean defaultValue) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> document, String key, BigDecimal defaultValue) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
