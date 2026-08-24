package online.yudream.base.infra.platform.graph.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import online.yudream.base.domain.platform.graph.valobj.*;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/** Capability-configured, lazily-created physical Neo4j Driver. */
@Service
public class Neo4jGraphDatabaseGateway implements GraphDatabaseGateway {
    private static final String PROJECTION_SCOPE = "tableCode:$tableCode,pluginCode:$pluginCode,namespace:$namespace,versionId:$versionId";
    private static final int PROJECTION_BATCH_SIZE = 500;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private volatile ConnectionSettings settings;
    private volatile Driver driver;

    public synchronized void reconfigure(Map<String, String> config) { String uri=value(config,"uri"),username=value(config,"username"),password=value(config,"password"),database=value(config,"database");if(!StringUtils.hasText(uri)||!StringUtils.hasText(username)||!StringUtils.hasText(password)||!StringUtils.hasText(database))throw new BizException("请完整配置 Neo4j URI、用户名、密码和数据库");closeDriver();settings=new ConnectionSettings(uri,username,password,database); }
    @Override public GraphQueryResult test(GraphConnection table) { return query(table, "RETURN $tableCode AS tableCode", Map.of()); }
    @Override public GraphQueryResult query(GraphConnection table, String cypher, Map<String,Object> params) { long start=System.currentTimeMillis();try(var session=openSession()){Map<String,Object> scoped=new LinkedHashMap<>(params==null?Map.of():params);scoped.put("tableCode",table.getCode());var result=session.run(cypher,scoped);List<Map<String,Object>> rows=new ArrayList<>();while(result.hasNext()){var record=result.next();Map<String,Object> row=new LinkedHashMap<>();for(String key:record.keys())row.put(key,toPlainObject(record.get(key)));rows.add(row);}return new GraphQueryResult(rows,result.consume().queryType().name(),elapsed(start),GraphQueryStatus.SUCCESS,null);}catch(Neo4jException|IllegalArgumentException ex){return new GraphQueryResult(List.of(),"执行失败",elapsed(start),GraphQueryStatus.FAILED,ex.getMessage());} }
    @Override
    public GraphProjectionReplaceResult replaceProjection(GraphConnection table, String pluginCode, GraphProjection projection) {
        ProjectionWriteContext context = new ProjectionWriteContext(ProjectionStage.DELETE_SCOPE, 0, 0);
        try (var session = openSession()) {
            return session.executeWrite(tx -> {
                Map<String, Object> scope = scope(table, pluginCode, projection.namespace(), projection.versionId());
                context.update(ProjectionStage.DELETE_SCOPE, 0, 0);
                consume(tx, "MATCH (n:PluginGraphProjection {" + PROJECTION_SCOPE + "}) DETACH DELETE n", scope, context);
                writeNodes(tx, scope, projection.nodes(), context);
                writeRelationships(tx, scope, projection.relationships(), context);
                context.update(ProjectionStage.VERIFY_NODE_COUNT, 0, projection.nodes().size());
                long nodes = count(tx, "MATCH (n:PluginGraphProjection {" + PROJECTION_SCOPE + "}) RETURN count(n) AS total", scope, context);
                requireCount(ProjectionFailureType.NODE_COUNT_MISMATCH, projection.nodes().size(), nodes, context);
                context.update(ProjectionStage.VERIFY_RELATIONSHIP_COUNT, 0, projection.relationships().size());
                long relationships = count(tx, "MATCH ()-[r:PluginGraphProjectionRelation {" + PROJECTION_SCOPE + "}]->() RETURN count(r) AS total", scope, context);
                requireCount(ProjectionFailureType.RELATIONSHIP_COUNT_MISMATCH, projection.relationships().size(), relationships, context);
                return new GraphProjectionReplaceResult(projection.namespace(), nodes, relationships);
            });
        } catch (ProjectionWriteException e) {
            throw e;
        } catch (Neo4jException | IllegalArgumentException e) {
            throw new ProjectionWriteException(ProjectionFailureType.NEO4J_WRITE_FAILED, context, e);
        }
    }

    @Override public GraphProjectionView readProjection(GraphConnection table,String pluginCode,GraphProjectionViewRequest request){Map<String,Object>s=scope(table,pluginCode,request.namespace(),request.versionId());try(var session=openSession()){return new GraphProjectionView(request.namespace(),request.versionId(),nodes(session,s,request.nodePage(),request.nodeSize()),relationships(session,s,request.relationshipPage(),request.relationshipSize()));}catch(Neo4jException|IllegalArgumentException e){throw new IllegalStateException("图投影读取失败",e);}}
    /** Opens a session on the active capability-configured Driver for trusted fixed-Cypher gateways. */
    public Session openSession() { ConnectionSettings current=settings(); return driver().session(SessionConfig.forDatabase(current.database())); }
    @Override public void close(String ignored){closeAll();} @Override public synchronized void closeAll(){closeDriver();settings=null;}

    void writeNodes(org.neo4j.driver.TransactionContext tx, Map<String, Object> scope, List<GraphProjectionNode> nodes) {
        writeNodes(tx, scope, nodes, new ProjectionWriteContext(ProjectionStage.WRITE_NODES, 0, 0));
    }

    private void writeNodes(org.neo4j.driver.TransactionContext tx, Map<String, Object> scope,
                            List<GraphProjectionNode> nodes, ProjectionWriteContext context) {
        for (int start = 0; start < nodes.size(); start += PROJECTION_BATCH_SIZE) {
            int end = Math.min(start + PROJECTION_BATCH_SIZE, nodes.size());
            context.update(ProjectionStage.WRITE_NODES, start / PROJECTION_BATCH_SIZE, end - start);
            try {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (GraphProjectionNode node : nodes.subList(start, end)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", node.id());
                    row.put("type", node.type());
                    row.put("propertiesJson", propertiesJson(node.properties()));
                    rows.add(row);
                }
                Map<String, Object> parameters = new LinkedHashMap<>(scope);
                parameters.put("rows", rows);
                long written = count(tx, "UNWIND $rows AS row CREATE (n:PluginGraphProjection {" + PROJECTION_SCOPE
                        + ",id:row.id,type:row.type,propertiesJson:row.propertiesJson}) RETURN count(n) AS total", parameters, context);
                requireCount(ProjectionFailureType.NODE_COUNT_MISMATCH, rows.size(), written, context);
            } catch (ProjectionWriteException e) {
                throw e;
            } catch (Neo4jException | IllegalArgumentException e) {
                throw new ProjectionWriteException(ProjectionFailureType.NEO4J_WRITE_FAILED, context, e);
            }
        }
    }

    void writeRelationships(org.neo4j.driver.TransactionContext tx, Map<String, Object> scope,
                            List<GraphProjectionRelationship> relationships) {
        writeRelationships(tx, scope, relationships,
                new ProjectionWriteContext(ProjectionStage.WRITE_RELATIONSHIPS, 0, 0));
    }

    private void writeRelationships(org.neo4j.driver.TransactionContext tx, Map<String, Object> scope,
                                    List<GraphProjectionRelationship> relationships, ProjectionWriteContext context) {
        for (int start = 0; start < relationships.size(); start += PROJECTION_BATCH_SIZE) {
            int end = Math.min(start + PROJECTION_BATCH_SIZE, relationships.size());
            context.update(ProjectionStage.WRITE_RELATIONSHIPS, start / PROJECTION_BATCH_SIZE, end - start);
            try {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (GraphProjectionRelationship relationship : relationships.subList(start, end)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", relationship.id());
                    row.put("type", relationship.type());
                    row.put("sourceId", relationship.sourceId());
                    row.put("targetId", relationship.targetId());
                    row.put("propertiesJson", propertiesJson(relationship.properties()));
                    rows.add(row);
                }
                Map<String, Object> parameters = new LinkedHashMap<>(scope);
                parameters.put("rows", rows);
                long written = count(tx, "UNWIND $rows AS row "
                        + "MATCH (s:PluginGraphProjection {" + PROJECTION_SCOPE + ",id:row.sourceId}) "
                        + "MATCH (t:PluginGraphProjection {" + PROJECTION_SCOPE + ",id:row.targetId}) "
                        + "CREATE (s)-[r:PluginGraphProjectionRelation {" + PROJECTION_SCOPE
                        + ",id:row.id,type:row.type,propertiesJson:row.propertiesJson}]->(t) RETURN count(r) AS total", parameters, context);
                requireCount(ProjectionFailureType.RELATIONSHIP_COUNT_MISMATCH, rows.size(), written, context);
            } catch (ProjectionWriteException e) {
                throw e;
            } catch (Neo4jException | IllegalArgumentException e) {
                throw new ProjectionWriteException(ProjectionFailureType.NEO4J_WRITE_FAILED, context, e);
            }
        }
    }

    private static void consume(org.neo4j.driver.TransactionContext tx, String cypher,
                                Map<String, Object> parameters, ProjectionWriteContext context) {
        try {
            tx.run(cypher, parameters).consume();
        } catch (Neo4jException | IllegalArgumentException e) {
            throw new ProjectionWriteException(ProjectionFailureType.NEO4J_WRITE_FAILED, context, e);
        }
    }

    private static long count(org.neo4j.driver.TransactionContext tx, String cypher,
                              Map<String, Object> parameters, ProjectionWriteContext context) {
        try {
            return tx.run(cypher, parameters).single().get("total").asLong();
        } catch (Neo4jException | IllegalArgumentException e) {
            throw new ProjectionWriteException(ProjectionFailureType.NEO4J_WRITE_FAILED, context, e);
        }
    }

    private static void requireCount(ProjectionFailureType type, long expected, long actual,
                                     ProjectionWriteContext context) {
        if (actual != expected) throw new ProjectionWriteException(type, context, null);
    }

    public static String failureType(Throwable error) {
        return failureDiagnostics(error).failureType();
    }

    public static ProjectionFailureDiagnostics failureDiagnostics(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ProjectionWriteException projectionError) return projectionError.diagnostics();
        }
        return ProjectionFailureDiagnostics.unclassified();
    }

    public record ProjectionFailureDiagnostics(String failureType, String stage, int batchIndex, int batchSize,
                                               String neo4jCode, String gqlStatus) {
        private static ProjectionFailureDiagnostics unclassified() {
            return new ProjectionFailureDiagnostics("UNCLASSIFIED", "UNCLASSIFIED", -1, 0,
                    "UNAVAILABLE", "UNAVAILABLE");
        }
    }

    private enum ProjectionFailureType {
        NODE_COUNT_MISMATCH,
        RELATIONSHIP_COUNT_MISMATCH,
        NEO4J_WRITE_FAILED
    }

    private enum ProjectionStage {
        DELETE_SCOPE,
        WRITE_NODES,
        WRITE_RELATIONSHIPS,
        VERIFY_NODE_COUNT,
        VERIFY_RELATIONSHIP_COUNT
    }

    private static final class ProjectionWriteContext {
        private ProjectionStage stage;
        private int batchIndex;
        private int batchSize;

        private ProjectionWriteContext(ProjectionStage stage, int batchIndex, int batchSize) {
            update(stage, batchIndex, batchSize);
        }

        private void update(ProjectionStage stage, int batchIndex, int batchSize) {
            this.stage = stage;
            this.batchIndex = batchIndex;
            this.batchSize = batchSize;
        }
    }

    private static final class ProjectionWriteException extends IllegalStateException {
        private final ProjectionFailureDiagnostics diagnostics;

        private ProjectionWriteException(ProjectionFailureType type, ProjectionWriteContext context, Throwable cause) {
            super(type.name(), cause);
            Neo4jException neo4jError = neo4jCause(cause);
            this.diagnostics = new ProjectionFailureDiagnostics(type.name(), context.stage.name(),
                    context.batchIndex, context.batchSize,
                    safeStatus(neo4jError == null ? null : neo4jError.code()),
                    safeStatus(neo4jError == null ? null : neo4jError.gqlStatus()));
        }

        private ProjectionFailureDiagnostics diagnostics() {
            return diagnostics;
        }
    }

    private static Neo4jException neo4jCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof Neo4jException neo4jError) return neo4jError;
        }
        return null;
    }

    private static String safeStatus(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,128}") ? value : "UNAVAILABLE";
    }

    private GraphProjectionPage<GraphProjectionNode> nodes(Session s,Map<String,Object> scope,int page,int size){long total=s.run("MATCH (n:PluginGraphProjection {"+PROJECTION_SCOPE+"}) RETURN count(n) AS total",scope).single().get("total").asLong();Map<String,Object>p=page(scope,page,size);var rows=s.run("MATCH (n:PluginGraphProjection {"+PROJECTION_SCOPE+"}) RETURN n.id AS id,n.type AS type,n.propertiesJson AS propertiesJson,n.properties AS properties ORDER BY n.id SKIP $skip LIMIT $limit",p);List<GraphProjectionNode>out=new ArrayList<>();while(rows.hasNext()){var r=rows.next();out.add(new GraphProjectionNode(r.get("id").asString(),r.get("type").asString(),readProperties(r)));}return new GraphProjectionPage<>(out,total,page,size);}
    private GraphProjectionPage<GraphProjectionRelationship> relationships(Session s,Map<String,Object> scope,int page,int size){long total=s.run("MATCH ()-[r:PluginGraphProjectionRelation {"+PROJECTION_SCOPE+"}]->() RETURN count(r) AS total",scope).single().get("total").asLong();Map<String,Object>p=page(scope,page,size);var rows=s.run("MATCH (a:PluginGraphProjection {"+PROJECTION_SCOPE+"})-[r:PluginGraphProjectionRelation {"+PROJECTION_SCOPE+"}]->(b) RETURN r.id AS id,r.type AS type,a.id AS sourceId,b.id AS targetId,r.propertiesJson AS propertiesJson,r.properties AS properties ORDER BY r.id SKIP $skip LIMIT $limit",p);List<GraphProjectionRelationship>out=new ArrayList<>();while(rows.hasNext()){var r=rows.next();out.add(new GraphProjectionRelationship(r.get("id").asString(),r.get("type").asString(),r.get("sourceId").asString(),r.get("targetId").asString(),readProperties(r)));}return new GraphProjectionPage<>(out,total,page,size);}
    private static String propertiesJson(Map<String,Object> properties){try{return JSON.writeValueAsString(properties==null?Map.of():properties);}catch(Exception e){throw new IllegalArgumentException("invalid projection properties",e);}}
    private static Map<String,Object> readProperties(org.neo4j.driver.Record record){try{var encoded=record.get("propertiesJson");if(encoded!=null&&!encoded.isNull()&&StringUtils.hasText(encoded.asString()))return Collections.unmodifiableMap(new LinkedHashMap<>(JSON.readValue(encoded.asString(),MAP_TYPE)));var legacy=record.get("properties");return legacy==null||legacy.isNull()?Map.of():legacy.asMap();}catch(Exception e){throw new IllegalArgumentException("invalid stored projection properties",e);}}
    private Map<String,Object> scope(GraphConnection t,String plugin,String namespace,String version){return Map.of("tableCode",t.getCode(),"pluginCode",plugin,"namespace",namespace,"versionId",version);} private Map<String,Object> page(Map<String,Object>s,int page,int size){Map<String,Object>p=new LinkedHashMap<>(s);p.put("skip",(long)(page-1)*size);p.put("limit",size);return p;}
    private Driver driver(){Driver result=driver;if(result==null){synchronized(this){result=driver;if(result==null){ConnectionSettings current=settings();result=GraphDatabase.driver(current.uri(),AuthTokens.basic(current.username(),current.password()));driver=result;}}}return result;} private ConnectionSettings settings(){ConnectionSettings current=settings;if(current==null)throw new BizException("Neo4j 物理连接未配置，请先在平台能力中配置");return current;} private void closeDriver(){if(driver!=null){driver.close();driver=null;}} private static String value(Map<String,String> config,String key){return config==null?null:config.get(key);} private static Object toPlainObject(org.neo4j.driver.Value value){return value==null||value.isNull()?null:value.asObject();} private static long elapsed(long start){return System.currentTimeMillis()-start;} private record ConnectionSettings(String uri,String username,String password,String database) {}
}
