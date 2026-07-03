package online.yudream.base.application.platform.graph.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.graph.assembler.GraphAssembler;
import online.yudream.base.application.platform.graph.cmd.GraphConnectionSaveCmd;
import online.yudream.base.application.platform.graph.cmd.GraphQueryCmd;
import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.application.platform.graph.query.GraphPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.graph.repo.GraphQueryLogRepo;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import online.yudream.base.domain.platform.graph.valobj.GraphQueryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GraphAppService {

    private static final String GRAPH_CAPABILITY_CODE = "neo4j";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final GraphConnectionRepo graphConnectionRepo;
    private final GraphQueryLogRepo graphQueryLogRepo;
    private final GraphDatabaseGateway graphDatabaseGateway;

    @Transactional(readOnly = true)
    public PageResult<GraphConnectionDTO> pageConnections(GraphPageQuery query) {
        PageResult<GraphConnection> page = graphConnectionRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(GraphAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public GraphConnectionDTO saveConnection(GraphConnectionSaveCmd cmd) {
        ensureGraphEnabled();
        GraphConnection connection = cmd.getId() == null ? createConnection(cmd) : connection(cmd.getId());
        connection.update(cmd.getName(), cmd.getUri(), cmd.getUsername(), cmd.getPassword(), cmd.getDatabase(), cmd.getStatus());
        return GraphAssembler.toDTO(graphConnectionRepo.save(connection));
    }

    @Transactional
    public void disableConnection(Long id) {
        ensureGraphEnabled();
        GraphConnection connection = connection(id);
        connection.disable();
        graphConnectionRepo.save(connection);
        graphDatabaseGateway.close(connection.getCode());
    }

    @Transactional
    public GraphQueryLogDTO testConnection(Long id) {
        ensureGraphEnabled();
        GraphConnection connection = activeConnection(id);
        GraphQueryResult result = graphDatabaseGateway.test(connection);
        return GraphAssembler.toDTO(saveLog(connection, "RETURN 1 AS ok", Map.of(), result));
    }

    @Transactional
    public GraphQueryLogDTO query(GraphQueryCmd cmd) {
        ensureGraphEnabled();
        if (!StringUtils.hasText(cmd.getCypher())) {
            throw new BizException("Cypher 不能为空");
        }
        GraphConnection connection = activeConnection(cmd.getConnectionId());
        GraphQueryResult result = graphDatabaseGateway.query(connection, cmd.getCypher(), cmd.getParams());
        return GraphAssembler.toDTO(saveLog(connection, cmd.getCypher(), cmd.getParams(), result));
    }

    @Transactional(readOnly = true)
    public PageResult<GraphQueryLogDTO> pageLogs(GraphPageQuery query) {
        PageResult<GraphQueryLog> page = graphQueryLogRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(GraphAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    private GraphConnection createConnection(GraphConnectionSaveCmd cmd) {
        if (graphConnectionRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("连接编码已存在");
        }
        return GraphConnection.create(cmd.getName(), cmd.getCode(), cmd.getUri(), cmd.getUsername(), cmd.getPassword(), cmd.getDatabase());
    }

    private GraphConnection activeConnection(Long id) {
        GraphConnection connection = connection(id);
        if (!connection.active()) {
            throw new BizException("图数据库连接已停用");
        }
        return connection;
    }

    private GraphConnection connection(Long id) {
        return graphConnectionRepo.findById(id).orElseThrow(() -> new BizException("图数据库连接不存在"));
    }

    private GraphQueryLog saveLog(GraphConnection connection, String cypher, Map<String, Object> params, GraphQueryResult result) {
        GraphQueryLog log = GraphQueryLog.builder()
                .connectionId(connection.getId())
                .connectionCode(connection.getCode())
                .cypher(cypher)
                .params(params == null ? Map.of() : params)
                .rows(result.rows())
                .summary(result.summary())
                .durationMillis(result.durationMillis())
                .status(result.status())
                .errorMessage(result.errorMessage())
                .executedAt(LocalDateTime.now())
                .build();
        return graphQueryLogRepo.save(log);
    }

    private void ensureGraphEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(GRAPH_CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("Neo4j 图数据库能力未启用");
        }
    }
}
