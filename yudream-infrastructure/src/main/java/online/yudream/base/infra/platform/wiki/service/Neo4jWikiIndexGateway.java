package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiEmbeddingGateway;
import online.yudream.base.domain.platform.wiki.service.WikiGraphExtractionGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiGraphRelation;
import online.yudream.base.domain.platform.wiki.valobj.WikiIndexProgress;
import online.yudream.base.domain.platform.wiki.valobj.WikiIndexSnapshot;
import online.yudream.base.domain.platform.wiki.valobj.WikiChunk;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.common.exception.BizException;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class Neo4jWikiIndexGateway implements WikiIndexGateway {

    private final String uri;
    private final String username;
    private final String password;
    private final String database;
    private final WikiEmbeddingGateway embeddingGateway;
    private final WikiGraphExtractionGateway graphExtractionGateway;
    private final GraphConnectionRepo graphConnections;

    public Neo4jWikiIndexGateway(@Value("${yudream.platform.wiki.neo4j.uri:bolt://localhost:7687}") String uri,
                                 @Value("${yudream.platform.wiki.neo4j.username:neo4j}") String username,
                                 @Value("${yudream.platform.wiki.neo4j.password:}") String password,
                                 @Value("${yudream.platform.wiki.neo4j.database:neo4j}") String database,
                                 WikiEmbeddingGateway embeddingGateway,
                                 WikiGraphExtractionGateway graphExtractionGateway,
                                 GraphConnectionRepo graphConnections) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.database = database;
        this.embeddingGateway = embeddingGateway;
        this.graphExtractionGateway = graphExtractionGateway;
        this.graphConnections = graphConnections;
    }

    @Override
    public void index(WikiSpace space, WikiPageVersion version, String path, Consumer<WikiIndexProgress> progress) {
        List<String> chunks = chunks(version.getMarkdown(), space.getChunkSize(), space.getChunkOverlap());
        progress.accept(new WikiIndexProgress("chunking", "已完成 Markdown 分块，共 " + chunks.size() + " 段", 20));
        if (space.getEmbeddingProviderCode().isBlank() || space.getEmbeddingModelCode().isBlank()) {
            throw new IllegalArgumentException("知识库未配置 Embedding Provider 和模型");
        }
        List<List<Float>> embeddings = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            int percent = 25 + (int) Math.round((index * 30d) / Math.max(chunks.size(), 1));
            progress.accept(new WikiIndexProgress("embedding", "正在向量化分块 " + (index + 1) + "/" + chunks.size(), percent));
            embeddings.add(embeddingGateway.embed(space.getEmbeddingProviderCode(), space.getEmbeddingModelCode(), List.of(chunks.get(index))).getFirst());
            progress.accept(new WikiIndexProgress("embedding", "分块 " + (index + 1) + "/" + chunks.size() + " 向量化完成", percent + 1));
        }
        progress.accept(new WikiIndexProgress("embedding", "全部 " + embeddings.size() + " 个分块向量化完成", 55));
        progress.accept(new WikiIndexProgress("graph", space.isGraphEnabled() ? "正在抽取知识图谱关系" : "已跳过图谱抽取", 65));
        List<WikiGraphRelation> relations = space.isGraphEnabled()
                ? graphExtractionGateway.extract(space.getGraphProviderCode(), space.getGraphModelCode(), version.getTitle(), version.getMarkdown())
                : List.of();
        progress.accept(new WikiIndexProgress("graph", "图谱抽取完成，共 " + relations.size() + " 条关系", 75));
        int dimensions = embeddings.getFirst().size();
        String label = vectorLabel(dimensions);
        String index = vectorIndex(dimensions);
        progress.accept(new WikiIndexProgress("neo4j", "正在写入 Neo4j 向量索引和图谱", 85));
        try (Driver driver = driver(space); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(database(space)))) {
            // Neo4j schema commands cannot share a transaction with data writes.
            session.executeWriteWithoutResult(tx -> tx.run("CREATE VECTOR INDEX " + index + " IF NOT EXISTS FOR (c:" + label + ") ON (c.embedding) OPTIONS {indexConfig: {`vector.dimensions`: $dimensions, `vector.similarity_function`: 'cosine'}}", Map.of("dimensions", dimensions)).consume());
            session.executeWriteWithoutResult(tx -> {
                tx.run("MATCH (c:WikiChunk {spaceId:$spaceId,nodeId:$nodeId}) SET c.active=false", Map.of("spaceId", space.getId(), "nodeId", version.getNodeId()));
                tx.run("MATCH (c:WikiChunk {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) DETACH DELETE c", Map.of("spaceId", space.getId(), "nodeId", version.getNodeId(), "versionId", version.getId()));
                for (int i = 0; i < chunks.size(); i++) {
                    tx.run("CREATE (c:WikiChunk:" + label + " {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId,sequence:$sequence,title:$title,path:$path,content:$content,embedding:$embedding,active:true})",
                            Map.of("spaceId", space.getId(), "nodeId", version.getNodeId(), "versionId", version.getId(), "sequence", i, "title", version.getTitle(), "path", path, "content", chunks.get(i), "embedding", embeddings.get(i)));
                }
                tx.run("MATCH ()-[r:WIKI_RELATES {spaceId:$spaceId,nodeId:$nodeId}]->() DELETE r", Map.of("spaceId", space.getId(), "nodeId", version.getNodeId()));
                for (WikiGraphRelation relation : relations) {
                    tx.run("MERGE (source:WikiEntity {spaceId:$spaceId,key:toLower($source),type:$sourceType}) " +
                                    "MERGE (target:WikiEntity {spaceId:$spaceId,key:toLower($target),type:$targetType}) " +
                                    "MERGE (source)-[r:WIKI_RELATES {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId,relation:$relation}]->(target) " +
                                    "SET r.confidence=$confidence",
                            Map.of("spaceId", space.getId(), "nodeId", version.getNodeId(), "versionId", version.getId(),
                                    "source", relation.source().trim(), "sourceType", type(relation.sourceType()),
                                    "target", relation.target().trim(), "targetType", type(relation.target()),
                                    "relation", relation.relation().trim(), "confidence", relation.confidence()));
                }
            });
        }
        progress.accept(new WikiIndexProgress("neo4j", "Neo4j 写入完成", 95));
    }

    @Override public void remove(WikiSpace space, Long nodeId, Long versionId) {
        try (Driver driver = driver(space); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(database(space)))) {
            session.run("MATCH (c:WikiChunk {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) DETACH DELETE c", Map.of("spaceId", space.getId(), "nodeId", nodeId, "versionId", versionId));
            session.run("MATCH ()-[r:WIKI_RELATES {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}]->() DELETE r", Map.of("spaceId", space.getId(), "nodeId", nodeId, "versionId", versionId));
        }
    }

    @Override public WikiIndexSnapshot inspect(WikiSpace space, Long nodeId, Long versionId) {
        try (Driver driver = driver(space); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(database(space)))) {
            var chunkRows = session.run("MATCH (c:WikiChunk {spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) RETURN c ORDER BY c.sequence", Map.of("spaceId", space.getId(), "nodeId", nodeId, "versionId", versionId));
            List<WikiChunk> chunks = new ArrayList<>();
            while (chunkRows.hasNext()) { var c = chunkRows.next().get("c").asNode(); chunks.add(new WikiChunk(space.getId(), nodeId, versionId, c.get("sequence").asInt(), c.get("title").asString(), c.get("path").asString(), c.get("content").asString())); }
            var relationRows = session.run("MATCH (source:WikiEntity {spaceId:$spaceId})-[r:WIKI_RELATES {nodeId:$nodeId,versionId:$versionId}]->(target:WikiEntity {spaceId:$spaceId}) RETURN source,r,target ORDER BY r.confidence DESC", Map.of("spaceId", space.getId(), "nodeId", nodeId, "versionId", versionId));
            List<WikiGraphRelation> relations = new ArrayList<>();
            while (relationRows.hasNext()) { var row = relationRows.next(); var source = row.get("source").asNode(); var target = row.get("target").asNode(); var relation = row.get("r").asRelationship(); relations.add(new WikiGraphRelation(source.get("key").asString(), source.get("type").asString(), relation.get("relation").asString(), target.get("key").asString(), target.get("type").asString(), relation.get("confidence").asDouble())); }
            return new WikiIndexSnapshot(chunks, relations);
        }
    }

    @Override public List<WikiSearchHit> search(WikiSpace space, String query, int topK, String pathPrefix, boolean graphExpansion) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) return List.of();
        if (space.getEmbeddingProviderCode().isBlank() || space.getEmbeddingModelCode().isBlank()) throw new IllegalArgumentException("知识库未配置 Embedding 模型");
        List<Float> vector = embeddingGateway.embed(space.getEmbeddingProviderCode(), space.getEmbeddingModelCode(), List.of(normalized)).getFirst();
        String index = vectorIndex(vector.size());
        try (Driver driver = driver(space); var session = driver.session(org.neo4j.driver.SessionConfig.forDatabase(database(space)))) {
            var result = session.run("CALL db.index.vector.queryNodes($index, $candidateLimit, $vector) YIELD node, score WHERE node.spaceId=$spaceId AND node.active=true AND ($path='' OR node.path STARTS WITH $path) RETURN node, score ORDER BY score DESC LIMIT $limit", Map.of("index", index, "candidateLimit", Math.clamp(topK * 4, 10, 100), "vector", vector, "spaceId", space.getId(), "path", pathPrefix == null ? "" : pathPrefix, "limit", Math.clamp(topK, 1, 30)));
            List<WikiSearchHit> hits = new ArrayList<>();
            while (result.hasNext()) { var row = result.next(); var c = row.get("node").asNode(); hits.add(new WikiSearchHit(row.get("score").asDouble(), c.get("nodeId").asLong(), c.get("title").asString(), c.get("path").asString(), c.get("content").asString())); }
            if (graphExpansion && !hits.isEmpty()) {
                List<Long> sourceNodeIds = hits.stream().map(WikiSearchHit::nodeId).distinct().toList();
                var graphRows = session.run("MATCH (entity:WikiEntity {spaceId:$spaceId})-[r1:WIKI_RELATES]->(:WikiEntity {spaceId:$spaceId}) " +
                                "WHERE r1.nodeId IN $sourceNodeIds " +
                                "MATCH (entity)-[r2:WIKI_RELATES]->(:WikiEntity {spaceId:$spaceId}) " +
                                "WHERE r2.nodeId <> r1.nodeId " +
                                "WITH DISTINCT r2.nodeId AS relatedNodeId, max(r1.confidence * r2.confidence) AS confidence " +
                                "MATCH (chunk:WikiChunk {spaceId:$spaceId,nodeId:relatedNodeId,active:true}) " +
                                "WHERE ($path='' OR chunk.path STARTS WITH $path) " +
                                "RETURN chunk, confidence LIMIT $limit",
                        Map.of("spaceId", space.getId(), "sourceNodeIds", sourceNodeIds, "path", pathPrefix == null ? "" : pathPrefix, "limit", Math.clamp(topK, 1, 30)));
                java.util.Set<Long> existing = hits.stream().map(WikiSearchHit::nodeId).collect(java.util.stream.Collectors.toSet());
                while (graphRows.hasNext() && hits.size() < Math.clamp(topK, 1, 30)) {
                    var row = graphRows.next(); var chunk = row.get("chunk").asNode(); long nodeId = chunk.get("nodeId").asLong();
                    if (existing.add(nodeId)) hits.add(new WikiSearchHit(row.get("confidence").asDouble() * 0.45d, nodeId,
                            chunk.get("title").asString(), chunk.get("path").asString(), chunk.get("content").asString()));
                }
                hits.sort(java.util.Comparator.comparingDouble(WikiSearchHit::score).reversed());
            }
            return hits;
        } catch (org.neo4j.driver.exceptions.ClientException error) {
            if (error.getMessage() != null && error.getMessage().contains("no such vector schema index")) {
                throw new BizException("知识库尚未建立向量索引，请先发布或重建索引");
            }
            throw error;
        }
    }

    private Driver driver(WikiSpace space) { GraphConnection connection = connection(space); return GraphDatabase.driver(connection == null ? uri : connection.getUri(), AuthTokens.basic(connection == null ? username : connection.getUsername(), connection == null ? password : connection.getPassword())); }
    private String database(WikiSpace space) { GraphConnection connection = connection(space); return connection == null ? database : connection.getDatabase(); }
    private GraphConnection connection(WikiSpace space) { if (space == null || space.getNeo4jConnectionCode() == null || space.getNeo4jConnectionCode().isBlank()) return null; GraphConnection connection = graphConnections.findByCode(space.getNeo4jConnectionCode()).orElseThrow(() -> new IllegalArgumentException("知识库选择的 Neo4j 连接不存在")); if (!connection.active()) throw new IllegalArgumentException("知识库选择的 Neo4j 连接已停用"); return connection; }
    private static String vectorLabel(int dimensions) { return "WikiChunkVector" + dimensions; }
    private static String vectorIndex(int dimensions) { return "wiki_chunk_vector_" + dimensions; }
    private static String type(String value) { return value == null || value.isBlank() ? "CONCEPT" : value.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_"); }
    private static List<String> chunks(String markdown, int size, int overlap) { String value = markdown == null ? "" : markdown.trim(); if (value.isEmpty()) return List.of(""); List<String> result = new ArrayList<>(); for (int from = 0; from < value.length();) { int end = Math.min(value.length(), from + size); result.add(value.substring(from, end)); if (end == value.length()) break; from = Math.max(from + 1, end - overlap); } return result; }
}
