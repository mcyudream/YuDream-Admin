package online.yudream.base.infra.platform.graph.service;

import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.valobj.GraphProjection;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionNode;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionRelationship;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionViewRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jGraphDatabaseGatewayIntegrationTest {
    private Neo4j neo4j;
    private Neo4jGraphDatabaseGateway gateway;

    @BeforeEach
    void setUp() {
        neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
        gateway = new Neo4jGraphDatabaseGateway();
        gateway.reconfigure(Map.of(
                "uri", neo4j.boltURI().toString(),
                "username", "neo4j",
                "password", "integration-test-password",
                "database", "neo4j"));
    }

    @AfterEach
    void tearDown() {
        gateway.closeAll();
        neo4j.close();
    }

    @Test
    void writesEmptyProjectionAndReplacesOnlyTheSameScope() {
        var empty = gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1", List.of(), List.of()));
        assertEquals(0, empty.nodeCount());
        assertEquals(0, empty.relationshipCount());

        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1",
                List.of(node("old-a", Map.of()), node("old-b", Map.of())),
                List.of(relationship("old-edge", "old-a", "old-b", Map.of()))));
        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1",
                List.of(node("new-only", Map.of("state", "current"))), List.of()));

        var view = read("assets", "v1", 10, 10);
        assertEquals(1, view.nodes().total());
        assertEquals("new-only", view.nodes().records().getFirst().id());
        assertEquals(0, view.relationships().total());
    }

    @Test
    void isolatesTheSameBusinessIdAcrossVersions() {
        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1",
                List.of(node("shared-id", Map.of("version", "v1"))), List.of()));
        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v2",
                List.of(node("shared-id", Map.of("version", "v2"))), List.of()));

        assertEquals("v1", read("assets", "v1", 10, 10).nodes().records().getFirst().properties().get("version"));
        assertEquals("v2", read("assets", "v2", 10, 10).nodes().records().getFirst().properties().get("version"));
    }

    @Test
    void writes363NodesAnd916RelationshipsThroughRealBatches() {
        List<GraphProjectionNode> nodes = new ArrayList<>();
        for (int index = 0; index < 363; index++) {
            nodes.add(node("node-" + index, Map.of("index", index)));
        }
        List<GraphProjectionRelationship> relationships = new ArrayList<>();
        for (int index = 0; index < 916; index++) {
            relationships.add(relationship("edge-" + index, "node-" + (index % 363),
                    "node-" + ((index + 1) % 363), Map.of("index", index)));
        }

        var result = gateway.replaceProjection(connection(), "mc-assets",
                projection("assets", "1.21.1", nodes, relationships));

        assertEquals(363, result.nodeCount());
        assertEquals(916, result.relationshipCount());
        var view = read("assets", "1.21.1", 100, 100);
        assertEquals(363, view.nodes().total());
        assertEquals(916, view.relationships().total());
    }

    @Test
    void rollsBackScopeDeletionWhenARealConstraintRejectsTheNodeBatch() {
        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1",
                List.of(node("must-survive", Map.of("state", "old"))), List.of()));
        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v2",
                List.of(node("conflicting-id", Map.of())), List.of()));
        try (var session = gateway.openSession()) {
            session.run("CREATE CONSTRAINT projection_id_unique FOR (n:PluginGraphProjection) REQUIRE n.id IS UNIQUE").consume();
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> gateway.replaceProjection(connection(), "mc-assets", projection("assets", "v1",
                        List.of(node("conflicting-id", Map.of("state", "new"))), List.of())));

        var diagnostic = Neo4jGraphDatabaseGateway.failureDiagnostics(failure);
        assertEquals("NEO4J_WRITE_FAILED", diagnostic.failureType());
        assertEquals("WRITE_NODES", diagnostic.stage());
        assertEquals(0, diagnostic.batchIndex());
        assertEquals(1, diagnostic.batchSize());
        assertEquals("Neo.ClientError.Schema.ConstraintValidationFailed", diagnostic.neo4jCode());
        assertNotEquals("UNAVAILABLE", diagnostic.gqlStatus());
        var rolledBack = read("assets", "v1", 10, 10);
        assertEquals(1, rolledBack.nodes().total());
        assertEquals("must-survive", rolledBack.nodes().records().getFirst().id());
        assertEquals("old", rolledBack.nodes().records().getFirst().properties().get("state"));
    }

    @Test
    void roundTripsJsonPropertiesWithoutFlatteningOrDroppingNulls() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", "diamond_sword");
        properties.put("damage", 7);
        properties.put("enabled", true);
        properties.put("optional", null);
        properties.put("tags", List.of("weapon", "rare"));
        properties.put("nested", Map.of("level", 3, "flags", List.of(false, true)));
        Map<String, Object> relationshipProperties = Map.of("weights", List.of(1, 2, 3));

        gateway.replaceProjection(connection(), "mc-assets", projection("assets", "json",
                List.of(node("source", properties), node("target", Map.of())),
                List.of(relationship("json-edge", "source", "target", relationshipProperties))));

        var view = read("assets", "json", 10, 10);
        assertEquals(properties, view.nodes().records().stream()
                .filter(node -> node.id().equals("source")).findFirst().orElseThrow().properties());
        assertEquals(relationshipProperties, view.relationships().records().getFirst().properties());
    }

    private online.yudream.base.domain.platform.graph.valobj.GraphProjectionView read(
            String namespace, String version, int nodeSize, int relationshipSize) {
        return gateway.readProjection(connection(), "mc-assets",
                new GraphProjectionViewRequest(namespace, version, 1, nodeSize, 1, relationshipSize));
    }

    private static GraphProjection projection(String namespace, String version, List<GraphProjectionNode> nodes,
                                              List<GraphProjectionRelationship> relationships) {
        return new GraphProjection(namespace, version, nodes, relationships);
    }

    private static GraphProjectionNode node(String id, Map<String, Object> properties) {
        return new GraphProjectionNode(id, "ITEM", properties);
    }

    private static GraphProjectionRelationship relationship(String id, String sourceId, String targetId,
                                                            Map<String, Object> properties) {
        return new GraphProjectionRelationship(id, "LINK", sourceId, targetId, properties);
    }

    private static GraphConnection connection() {
        return GraphConnection.builder().name("MC Assets").code("mc-assets-graph")
                .status(GraphConnectionStatus.ACTIVE).build();
    }
}
