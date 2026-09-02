package online.yudream.base.application.platform.graph.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.graph.assembler.GraphAssembler;
import online.yudream.base.application.platform.graph.cmd.GraphConnectionSaveCmd;
import online.yudream.base.application.platform.graph.cmd.GraphQueryCmd;
import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.application.platform.graph.query.GraphPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.graph.repo.GraphQueryLogRepo;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Application service for logical graph tables. */
@Service @RequiredArgsConstructor
public class GraphAppService {
    private static final String GRAPH_CAPABILITY_CODE="neo4j";
    private final CapabilityAppService capabilityAppService; private final GraphConnectionRepo graphConnectionRepo;
    private final GraphQueryLogRepo graphQueryLogRepo; private final GraphDatabaseGateway graphDatabaseGateway;
    @Transactional(readOnly=true) public PageResult<GraphConnectionDTO> pageTables(GraphPageQuery q){ensureGraphEnabled();PageResult<GraphConnection>p=graphConnectionRepo.page(q.getKeyword(),q.getStatus(),q.getPage(),q.getSize());return new PageResult<>(p.getRecords().stream().map(GraphAssembler::toDTO).toList(),p.getTotal(),p.getPage(),p.getSize());}
    @Transactional public GraphConnectionDTO saveTable(GraphConnectionSaveCmd cmd){ensureGraphEnabled();GraphConnection table=cmd.getId()==null?create(cmd):table(cmd.getId());table.update(cmd.getName(),cmd.getDescription(),cmd.getStatus());table.replaceAuthorizedPlugins(plugins(cmd.getAuthorizedPluginCodes()));return GraphAssembler.toDTO(graphConnectionRepo.save(table));}
    @Transactional public void disableTable(Long id){ensureGraphEnabled();GraphConnection t=table(id);t.disable();graphConnectionRepo.save(t);}
    @Transactional public void enableTable(Long id){ensureGraphEnabled();GraphConnection t=table(id);t.activate();graphConnectionRepo.save(t);}
    @Transactional public GraphQueryLogDTO testTable(Long id){ensureGraphEnabled();GraphConnection t=active(id);return GraphAssembler.toDTO(log(t,"diagnostic",Map.of(),graphDatabaseGateway.test(t)));}
    @Transactional(readOnly=true) public PageResult<GraphQueryLogDTO> pageLogs(GraphPageQuery q){ensureGraphEnabled();PageResult<GraphQueryLog>p=graphQueryLogRepo.page(q.getKeyword(),q.getPage(),q.getSize());return new PageResult<>(p.getRecords().stream().map(GraphAssembler::toDTO).toList(),p.getTotal(),p.getPage(),p.getSize());}
    private GraphConnection create(GraphConnectionSaveCmd c){if(graphConnectionRepo.findByCode(c.getCode()).isPresent())throw new BizException("逻辑图表编码已存在");return GraphConnection.create(c.getName(),c.getCode(),c.getDescription());}
    private Set<String> plugins(Set<String> values){LinkedHashSet<String>out=new LinkedHashSet<>();if(values!=null)for(String v:values){if(v==null||v.isBlank())throw new BizException("授权插件编码不能为空");out.add(v.trim());}return out;}
    private GraphConnection active(Long id){GraphConnection t=table(id);if(!t.active())throw new BizException("逻辑图表已停用");return t;}
    private GraphConnection table(Long id){return graphConnectionRepo.findById(id).orElseThrow(()->new BizException("逻辑图表不存在"));}
    private GraphQueryLog log(GraphConnection t,String cypher,Map<String,Object>params,online.yudream.base.domain.platform.graph.valobj.GraphQueryResult r){return graphQueryLogRepo.save(GraphQueryLog.builder().tableId(t.getId()).tableCode(t.getCode()).cypher(cypher).params(params).rows(r.rows()).summary(r.summary()).durationMillis(r.durationMillis()).status(r.status()).errorMessage(r.errorMessage()).executedAt(LocalDateTime.now()).build());}
    private void ensureGraphEnabled(){capabilityAppService.ensureEnabled(GRAPH_CAPABILITY_CODE,"Neo4j 图数据库");}
}
