package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryHit;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryModelOption;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryStatus;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
public class PluginSemanticMemoryFrameworkService implements PluginSemanticMemoryService {

    private static final String AI_CAPABILITY = "ai";
    private static final String NEO4J_CAPABILITY = "neo4j";
    private final CapabilityModuleRepo capabilities;
    private final AiProviderConfigParser providerConfigParser;
    private final ObjectMapper objectMapper;
    private final String defaultUri;
    private final String defaultUsername;
    private final String defaultPassword;
    private final String defaultDatabase;

    public PluginSemanticMemoryFrameworkService(CapabilityModuleRepo capabilities,
                                                AiProviderConfigParser providerConfigParser,
                                                ObjectMapper objectMapper,
                                                @Value("${yudream.platform.semantic-memory.neo4j.uri:bolt://localhost:7687}") String defaultUri,
                                                @Value("${yudream.platform.semantic-memory.neo4j.username:neo4j}") String defaultUsername,
                                                @Value("${yudream.platform.semantic-memory.neo4j.password:}") String defaultPassword,
                                                @Value("${yudream.platform.semantic-memory.neo4j.database:neo4j}") String defaultDatabase) {
        this.capabilities = capabilities;
        this.providerConfigParser = providerConfigParser;
        this.objectMapper = objectMapper;
        this.defaultUri = defaultUri;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
        this.defaultDatabase = defaultDatabase;
    }

    public static PluginSemanticMemoryService unavailable() {
        return new PluginSemanticMemoryService() {
            @Override public PluginSemanticMemoryStatus status() { return PluginSemanticMemoryStatus.unavailable("Semantic memory capability is unavailable"); }
            @Override public CompletionStage<Void> index(PluginSemanticMemoryRecord record) { return CompletableFuture.completedFuture(null); }
            @Override public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletionStage<Void> delete(String namespace, String id) { return CompletableFuture.completedFuture(null); }
        };
    }

    @Override
    public PluginSemanticMemoryStatus status() {
        if (!enabled(NEO4J_CAPABILITY)) return PluginSemanticMemoryStatus.unavailable("Neo4j capability is disabled");
        if (!enabled(AI_CAPABILITY)) return PluginSemanticMemoryStatus.unavailable("AI capability is disabled");
        List<PluginSemanticMemoryModelOption> models = providers().stream()
                .flatMap(provider -> provider.embeddingModels().stream()
                        .map(model -> new PluginSemanticMemoryModelOption(provider.code(), provider.displayName(), model, model)))
                .toList();
        return models.isEmpty()
                ? PluginSemanticMemoryStatus.unavailable("No embedding model is configured")
                : new PluginSemanticMemoryStatus(true, "Semantic memory is available", models);
    }

    @Override
    public CompletionStage<Void> index(PluginSemanticMemoryRecord record) {
        if (!validRecord(record) || !available(record.providerCode(), record.modelCode())) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                List<Float> embedding = embed(record.providerCode(), record.modelCode(), record.content());
                if (!embedding.isEmpty()) upsert(record, embedding);
            } catch (Exception ignored) {
                // Long-term memory is optional and must not interrupt chatbot replies.
            }
        });
    }

    @Override
    public CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query) {
        if (!validQuery(query) || !available(query.providerCode(), query.modelCode())) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Float> embedding = embed(query.providerCode(), query.modelCode(), query.text());
                return embedding.isEmpty() ? List.<PluginSemanticMemoryHit>of() : search(query, embedding);
            } catch (Exception ignored) {
                return List.of();
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(String namespace, String id) {
        if (!StringUtils.hasText(namespace) || !status().available()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try (var driver = driver(); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(connection().database()))) {
                String statement = StringUtils.hasText(id)
                        ? "MATCH (n:PluginMemoryChunk {namespace:$namespace,id:$id}) DETACH DELETE n"
                        : "MATCH (n:PluginMemoryChunk {namespace:$namespace}) DETACH DELETE n";
                session.executeWriteWithoutResult(tx -> tx.run(statement, Map.of("namespace", namespace, "id", id == null ? "" : id)));
            } catch (Exception ignored) {
                // Cleanup is best effort because semantic memory is optional.
            }
        });
    }

    private void upsert(PluginSemanticMemoryRecord record, List<Float> embedding) throws Exception {
        int dimensions = embedding.size();
        String label = "PluginMemoryVector" + dimensions;
        String index = "plugin_memory_vector_" + dimensions;
        String metadataJson = metadataJson(record.metadata());
        try (var driver = driver(); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(connection().database()))) {
            session.run("CREATE VECTOR INDEX " + index + " IF NOT EXISTS FOR (n:" + label + ") ON (n.embedding) OPTIONS {indexConfig: {`vector.dimensions`: $dimensions, `vector.similarity_function`: 'cosine'}}", Map.of("dimensions", dimensions)).consume();
            session.executeWriteWithoutResult(tx -> {
                tx.run("MERGE (n:PluginMemoryChunk:" + label + " {namespace:$namespace,id:$id}) " +
                                "SET n.content=$content,n.embedding=$embedding,n.providerCode=$providerCode,n.modelCode=$modelCode,n.metadataJson=$metadataJson,n.updatedAt=timestamp()",
                        Map.of("namespace", record.namespace(), "id", record.id(), "content", record.content(), "embedding", embedding,
                                "providerCode", record.providerCode(), "modelCode", record.modelCode(), "metadataJson", metadataJson));
            });
        }
    }

    private List<PluginSemanticMemoryHit> search(PluginSemanticMemoryQuery query, List<Float> embedding) {
        String index = "plugin_memory_vector_" + embedding.size();
        int candidateLimit = Math.clamp(query.limit() * 4, 10, 100);
        try (var driver = driver(); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(connection().database()))) {
            var rows = session.run("CALL db.index.vector.queryNodes($index, $candidateLimit, $embedding) YIELD node, score " +
                            "WHERE node.namespace=$namespace AND node.providerCode=$providerCode AND node.modelCode=$modelCode " +
                            "RETURN node,score ORDER BY score DESC LIMIT $limit",
                    Map.of("index", index, "candidateLimit", candidateLimit, "embedding", embedding, "namespace", query.namespace(),
                            "providerCode", query.providerCode(), "modelCode", query.modelCode(), "limit", query.limit()));
            List<PluginSemanticMemoryHit> hits = new ArrayList<>();
            while (rows.hasNext()) {
                var row = rows.next();
                var node = row.get("node").asNode();
                hits.add(new PluginSemanticMemoryHit(node.get("id").asString(), node.get("content").asString(), row.get("score").asDouble(),
                        metadata(node.get("metadataJson").isNull() ? "" : node.get("metadataJson").asString())));
            }
            return hits;
        }
    }

    private List<Float> embed(String providerCode, String modelCode, String content) {
        AiProviderEndpoint provider = providers().stream()
                .filter(item -> item.code().equalsIgnoreCase(providerCode))
                .filter(item -> item.embeddingModels().contains(modelCode))
                .findFirst().orElseThrow();
        Map<String, Object> response = RestClient.create().post().uri(provider.endpointBaseUrl() + "/embeddings")
                .contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + provider.apiKey())
                .body(Map.of("model", modelCode, "input", content)).retrieve().body(Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof List<?> rows) || rows.isEmpty() || !(rows.getFirst() instanceof Map<?, ?> row)) return List.of();
        Object raw = row.get("embedding");
        if (!(raw instanceof List<?> values)) return List.of();
        return values.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::floatValue).toList();
    }

    private boolean available(String providerCode, String modelCode) {
        return status().available() && providers().stream().anyMatch(item -> item.code().equalsIgnoreCase(providerCode)
                && item.embeddingModels().contains(modelCode) && StringUtils.hasText(item.apiKey()));
    }

    private boolean validRecord(PluginSemanticMemoryRecord record) {
        return record != null && StringUtils.hasText(record.namespace()) && StringUtils.hasText(record.id())
                && StringUtils.hasText(record.content()) && StringUtils.hasText(record.providerCode()) && StringUtils.hasText(record.modelCode());
    }

    private boolean validQuery(PluginSemanticMemoryQuery query) {
        return query != null && StringUtils.hasText(query.namespace()) && StringUtils.hasText(query.text())
                && StringUtils.hasText(query.providerCode()) && StringUtils.hasText(query.modelCode());
    }

    private boolean enabled(String code) {
        return capabilities.findByCode(code).map(item -> Boolean.TRUE.equals(item.getEnabled())).orElse(false);
    }

    private List<AiProviderEndpoint> providers() {
        Map<String, String> config = capabilities.findByCode(AI_CAPABILITY)
                .map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
        return providerConfigParser.parse(config).stream().filter(AiProviderEndpoint::enabled).toList();
    }

    private Connection connection() {
        Map<String, String> config = capabilities.findByCode(NEO4J_CAPABILITY)
                .map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
        return new Connection(config.getOrDefault("uri", defaultUri), config.getOrDefault("username", defaultUsername),
                config.getOrDefault("password", defaultPassword), config.getOrDefault("database", defaultDatabase));
    }

    private org.neo4j.driver.Driver driver() {
        Connection connection = connection();
        return GraphDatabase.driver(connection.uri(), AuthTokens.basic(connection.username(), connection.password()));
    }

    private Map<String, Object> metadata(String json) {
        try { return StringUtils.hasText(json) ? objectMapper.readValue(json, new TypeReference<>() { }) : Map.of(); }
        catch (Exception ignored) { return Map.of(); }
    }

    private String metadataJson(Map<String, Object> metadata) {
        try { return objectMapper.writeValueAsString(metadata); }
        catch (Exception ignored) { return "{}"; }
    }

    private record Connection(String uri, String username, String password, String database) { }
}
