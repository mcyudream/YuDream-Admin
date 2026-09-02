package online.yudream.base.infra.platform.plugin.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import online.yudream.base.domain.platform.graph.valobj.GraphProjection;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionNode;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionPage;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionReplaceResult;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionView;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionViewRequest;
import online.yudream.base.domain.platform.graph.valobj.GraphQueryResult;
import online.yudream.base.plugin.spi.system.graph.PluginGraphBindingStatus;
import online.yudream.base.plugin.spi.system.graph.PluginGraphCompleteProjection;
import online.yudream.base.plugin.spi.system.graph.PluginGraphErrorCode;
import online.yudream.base.plugin.spi.system.graph.PluginGraphProjection;
import online.yudream.base.plugin.spi.system.graph.PluginGraphProjectionNode;
import online.yudream.base.plugin.spi.system.graph.PluginGraphProjectionReadRequest;
import online.yudream.base.plugin.spi.system.graph.PluginGraphProjectionRequest;
import online.yudream.base.plugin.spi.system.graph.PluginGraphProjectionViewRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginGraphFrameworkServiceTest {

    @Test
    void listsOnlyActiveAuthorizedConnectionsForScope() {
        Fixture fixture = new Fixture();

        var authorized = fixture.service.scoped("project-progress").tables();
        var unauthorized = fixture.service.scoped("other-plugin").tables();

        assertEquals(1, authorized.size());
        assertEquals("wiki-default", authorized.getFirst().code());
        assertTrue(unauthorized.isEmpty());
    }

    @Test
    void reportsOnlyTheUniqueAuthorizedActiveBindingWithoutProjectionAccess() {
        Fixture fixture = new Fixture();

        PluginGraphBindingStatus result = fixture.service.scoped("project-progress").bindingStatus();

        assertTrue(result.available());
        assertEquals("wiki-default", result.table().code());
        assertEquals("wiki-default", result.table().name());
        assertEquals("test logical table", result.table().description());
        assertEquals(0, fixture.gateway.calls);
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void reportsTableFreeBindingFailureWithoutProjectionAccess() {
        Fixture fixture = new Fixture();

        var missing = fixture.service.scoped("other-plugin").bindingStatus();
        assertFalse(missing.available());
        assertEquals(PluginGraphErrorCode.TABLE_BINDING_NOT_FOUND, missing.error().code());

        fixture.connections.additionalConnection();
        var ambiguous = fixture.service.scoped("project-progress").bindingStatus();
        assertFalse(ambiguous.available());
        assertEquals(PluginGraphErrorCode.TABLE_BINDING_AMBIGUOUS, ambiguous.error().code());
        assertEquals(0, fixture.gateway.calls);
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void reportsUnavailableBindingCapabilityWithoutProjectionAccess() {
        Fixture fixture = new Fixture();
        fixture.capabilities.fail = true;

        var result = fixture.service.scoped("project-progress").bindingStatus();

        assertFalse(result.available());
        assertEquals(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, result.error().code());
        assertEquals(0, fixture.gateway.calls);
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void bindsVersionedProjectionToRuntimePluginScope() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                "wiki-default", "resources", "v1", List.of(new PluginGraphProjectionNode("task-1", "TASK", Map.of("title", "Build"))), List.of()));

        assertTrue(result.success());
        assertEquals("resources", result.namespace());
        assertEquals("project-progress", fixture.gateway.pluginCode);
        assertEquals("wiki-default", fixture.gateway.tableCode);
        assertEquals("resources", fixture.gateway.projection.namespace());
        assertEquals("v1", fixture.gateway.projection.versionId());
        assertEquals(1, result.nodeCount());
        assertEquals(1, fixture.capabilities.calls);
    }

    @Test
    void readsOnlyTheRuntimePluginScopedVersionedProjection() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionViewRequest(
                "wiki-default", "resources", "v1", 1, 20, 2, 10));

        assertTrue(result.success());
        assertEquals("resources", result.namespace());
        assertEquals("v1", result.versionId());
        assertEquals("project-progress", fixture.gateway.pluginCode);
        assertEquals("wiki-default", fixture.gateway.tableCode);
        assertEquals("resources", fixture.gateway.viewRequest.namespace());
        assertEquals("v1", fixture.gateway.viewRequest.versionId());
        assertEquals(1, result.nodes().total());
        assertEquals(2, result.relationships().page());
        assertEquals(1, fixture.gateway.readCalls);
    }

    @Test
    void bindsTableFreeProjectionToTheUniqueAuthorizedActiveTable() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjection(
                "resources", "v1", List.of(new PluginGraphProjectionNode("task-1", "TASK", Map.of())), List.of()));

        assertTrue(result.success());
        assertEquals("project-progress", fixture.gateway.pluginCode);
        assertEquals("wiki-default", fixture.gateway.tableCode);
        assertEquals(1, fixture.gateway.calls);
    }

    @Test
    void readsTableFreeProjectionFromTheUniqueAuthorizedActiveTable() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionReadRequest(
                "resources", "v1", 1, 20, 2, 10));

        assertTrue(result.success());
        assertEquals("project-progress", fixture.gateway.pluginCode);
        assertEquals("wiki-default", fixture.gateway.tableCode);
        assertEquals(1, fixture.gateway.readCalls);
    }

    @Test
    void rejectsTableFreeProjectionWhenNoTableIsBound() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("other-plugin").replaceProjection(new PluginGraphProjection(
                "resources", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_BINDING_NOT_FOUND, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void rejectsTableFreeProjectionWhenMultipleTablesAreBoundWithoutChoosingOne() {
        Fixture fixture = new Fixture();
        fixture.connections.additionalConnection();

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionReadRequest(
                "resources", "v1", 1, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_BINDING_AMBIGUOUS, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void rejectsInvalidTableFreeProjectionBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionReadRequest(
                "", "v1", 1, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.INVALID_PROJECTION, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void returnsStructuredTableFreeReadFailureWhenCapabilityIsUnavailable() {
        Fixture fixture = new Fixture();
        fixture.capabilities.fail = true;

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionReadRequest(
                "resources", "v1", 1, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void returnsStructuredReadFailureWhenCapabilityIsUnavailable() {
        Fixture fixture = new Fixture();
        fixture.capabilities.fail = true;

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionViewRequest(
                "wiki-default", "resources", "v1", 1, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void rejectsUnauthorizedProjectionReadBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("other-plugin").readProjection(new PluginGraphProjectionViewRequest(
                "wiki-default", "resources", "v1", 1, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_UNAUTHORIZED, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void rejectsInvalidProjectionReadPageBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionViewRequest(
                "wiki-default", "resources", "v1", 0, 20, 1, 20));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.INVALID_PROJECTION, result.error().code());
        assertEquals(0, fixture.gateway.readCalls);
    }

    @Test
    void returnsStructuredFailureWhenCapabilityIsUnavailable() {
        Fixture fixture = new Fixture();
        fixture.capabilities.fail = true;

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                "wiki-default", "resources", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void rejectsMissingConnectionBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                "missing", "resources", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_NOT_FOUND, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void rejectsDisabledConnectionBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                "wiki-disabled", "resources", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_DISABLED, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void rejectsUnauthorizedProjectionBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("other-plugin").replaceProjection(new PluginGraphProjectionRequest(
                "wiki-default", "resources", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.TABLE_UNAUTHORIZED, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void rejectsInvalidProjectionBeforeGateway() {
        Fixture fixture = new Fixture();

        var result = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                "wiki-default", "", "v1", List.of(), List.of()));

        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.INVALID_PROJECTION, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void replacesCompleteProjectionThroughUniqueAutomaticBinding() {
        Fixture fixture = new Fixture();
        var result = fixture.service.scoped("project-progress").replaceCompleteProjection(new PluginGraphCompleteProjection(
                "resources", "v1", List.of(new PluginGraphProjectionNode("task-1", "TASK", Map.of("nested", Map.of("state", "open")))), List.of()));
        assertTrue(result.success());
        assertEquals("wiki-default", fixture.gateway.tableCode);
        assertEquals(1, fixture.gateway.calls);
    }

    @Test
    void rejectsCompleteProjectionOverLimitBeforeGateway() {
        Fixture fixture = new Fixture();
        var nodes = java.util.stream.IntStream.range(0, 20_001)
                .mapToObj(index -> new PluginGraphProjectionNode("node-" + index, "TASK", Map.of())).toList();
        var result = fixture.service.scoped("project-progress").replaceCompleteProjection(
                new PluginGraphCompleteProjection("resources", "v1", nodes, List.of()));
        assertFalse(result.success());
        assertEquals(PluginGraphErrorCode.PROJECTION_LIMIT_EXCEEDED, result.error().code());
        assertEquals(0, fixture.gateway.calls);
    }

    @Test
    void logsOnlySafeStructuredFieldsForUnexpectedProjectionFailures() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginGraphFrameworkService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Fixture fixture = new Fixture();
            fixture.gateway.failWrites = true;
            var write = fixture.service.scoped("project-progress").replaceProjection(new PluginGraphProjectionRequest(
                    "wiki-default", "resources", "v1", List.of(new PluginGraphProjectionNode("task-1", "TASK", Map.of("secret", "should-not-log"))), List.of()));
            var completeWrite = fixture.service.scoped("project-progress").replaceCompleteProjection(new PluginGraphCompleteProjection(
                    "resources", "v1", List.of(new PluginGraphProjectionNode("task-2", "TASK", Map.of("secret", "should-not-log"))), List.of()));
            fixture.gateway.failWrites = false;
            fixture.gateway.failReads = true;
            var read = fixture.service.scoped("project-progress").readProjection(new PluginGraphProjectionViewRequest(
                    "wiki-default", "resources", "v1", 1, 20, 1, 20));

            assertEquals(PluginGraphErrorCode.PROJECTION_FAILED, write.error().code());
            assertEquals(PluginGraphErrorCode.PROJECTION_FAILED, completeWrite.error().code());
            assertEquals(PluginGraphErrorCode.PROJECTION_FAILED, read.error().code());
            assertEquals(3, appender.list.size());
            String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(messages.contains("operation=replaceProjection"));
            assertTrue(messages.contains("operation=replaceCompleteProjection"));
            assertTrue(messages.contains("operation=readProjection"));
            assertTrue(messages.contains("pluginCode=project-progress"));
            assertTrue(messages.contains("namespace=resources"));
            assertTrue(messages.contains("version=v1"));
            assertTrue(messages.contains("nodeCount=1"));
            assertTrue(messages.contains("relationshipCount=0"));
            assertTrue(messages.contains("errorCode=PROJECTION_FAILED"));
            assertTrue(messages.contains("errorClass=java.lang.IllegalStateException"));
            assertTrue(messages.contains("failureType=UNCLASSIFIED"));
            assertTrue(messages.contains("stage=UNCLASSIFIED"));
            assertTrue(messages.contains("batchIndex=-1"));
            assertTrue(messages.contains("batchSize=0"));
            assertTrue(messages.contains("neo4jCode=UNAVAILABLE"));
            assertTrue(messages.contains("gqlStatus=UNAVAILABLE"));
            assertFalse(messages.contains("should-not-log"));
            assertFalse(messages.contains("driver-uri-and-credentials-must-not-log"));
            assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void doesNotLogExpectedCapabilityOrBindingFailures() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginGraphFrameworkService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Fixture unavailable = new Fixture();
            unavailable.capabilities.fail = true;
            var capabilityFailure = unavailable.service.scoped("project-progress").replaceProjection(new PluginGraphProjection(
                    "resources", "v1", List.of(), List.of()));
            Fixture unbound = new Fixture();
            var bindingFailure = unbound.service.scoped("other-plugin").replaceProjection(new PluginGraphProjection(
                    "resources", "v1", List.of(), List.of()));

            assertEquals(PluginGraphErrorCode.CAPABILITY_UNAVAILABLE, capabilityFailure.error().code());
            assertEquals(PluginGraphErrorCode.TABLE_BINDING_NOT_FOUND, bindingFailure.error().code());
            assertTrue(appender.list.isEmpty());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static final class Fixture {
        private final TestCapabilityAppService capabilities = new TestCapabilityAppService();
        private final TestGraphConnectionRepo connections = new TestGraphConnectionRepo();
        private final TestGraphDatabaseGateway gateway = new TestGraphDatabaseGateway();
        private final PluginGraphFrameworkService service = new PluginGraphFrameworkService(capabilities, connections, gateway);
    }

    private static final class TestCapabilityAppService extends CapabilityAppService {
        private boolean fail;
        private int calls;
        private TestCapabilityAppService() { super(null, List.of()); }
        @Override public void ensureEnabled(String code, String name) {
            calls++;
            if (fail) throw new IllegalStateException("disabled");
        }
    }

    private static final class TestGraphConnectionRepo implements GraphConnectionRepo {
        private final GraphConnection connection = connection("wiki-default", GraphConnectionStatus.ACTIVE);
        private final GraphConnection disabledConnection = connection("wiki-disabled", GraphConnectionStatus.DISABLED);
        private final List<GraphConnection> activeAuthorizedConnections = new java.util.ArrayList<>(List.of(connection));

        @Override public GraphConnection save(GraphConnection item) { return item; }
        @Override public Optional<GraphConnection> findById(Long id) { return Optional.empty(); }
        @Override public Optional<GraphConnection> findByCode(String code) {
            if (connection.getCode().equals(code)) {
                return Optional.of(connection);
            }
            return disabledConnection.getCode().equals(code) ? Optional.of(disabledConnection) : Optional.empty();
        }
        @Override public PageResult<GraphConnection> page(String keyword, GraphConnectionStatus status, int page, int size) { return new PageResult<>(List.of(connection), 1, page, size); }
        @Override public List<GraphConnection> findActiveAuthorizedByPluginCode(String pluginCode, int limit) {
            return activeAuthorizedConnections.stream().filter(connection -> connection.authorizedFor(pluginCode))
                    .limit(limit).toList();
        }

        private void additionalConnection() {
            activeAuthorizedConnections.add(connection("wiki-secondary", GraphConnectionStatus.ACTIVE));
        }

        private GraphConnection connection(String code, GraphConnectionStatus status) {
            return GraphConnection.builder().name(code).code(code).description("test logical table")
                    .status(status).authorizedPluginCodes(Set.of("project-progress")).build();
        }
    }

    private static final class TestGraphDatabaseGateway implements GraphDatabaseGateway {
        private String pluginCode;
        private String tableCode;
        private GraphProjection projection;
        private GraphProjectionViewRequest viewRequest;
        private int calls;
        private int readCalls;
        private boolean failWrites;
        private boolean failReads;
        @Override public GraphQueryResult test(GraphConnection connection) { return null; }
        @Override public GraphQueryResult query(GraphConnection connection, String cypher, Map<String, Object> params) { return null; }
        @Override public GraphProjectionReplaceResult replaceProjection(GraphConnection connection, String pluginCode, GraphProjection projection) {
            calls++;
            this.pluginCode = pluginCode;
            this.tableCode = connection.getCode();
            this.projection = projection;
            if (failWrites) throw new IllegalStateException("driver-uri-and-credentials-must-not-log");
            return new GraphProjectionReplaceResult(projection.namespace(), projection.nodes().size(), projection.relationships().size());
        }
        @Override public GraphProjectionView readProjection(GraphConnection connection, String pluginCode, GraphProjectionViewRequest request) {
            readCalls++;
            this.pluginCode = pluginCode;
            this.tableCode = connection.getCode();
            this.viewRequest = request;
            if (failReads) throw new IllegalStateException("driver-uri-and-credentials-must-not-log");
            return new GraphProjectionView(request.namespace(), request.versionId(),
                    new GraphProjectionPage<>(List.of(new GraphProjectionNode("task-1", "TASK", Map.of())), 1, request.nodePage(), request.nodeSize()),
                    new GraphProjectionPage<>(List.of(), 0, request.relationshipPage(), request.relationshipSize()));
        }
        @Override public void close(String code) { }
        @Override public void closeAll() { }
    }
}
