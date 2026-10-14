package online.yudream.base.infra.platform.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import online.yudream.base.domain.platform.agent.repo.AgentWorkflowCheckpointRepo;
import online.yudream.base.infra.platform.agent.dataobj.AgentWorkflowCheckpointDO;
import online.yudream.base.infra.platform.agent.mapper.AgentWorkflowCheckpointInfraMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AgentWorkflowCheckpointRepoImpl implements AgentWorkflowCheckpointRepo {
    private final MongoTemplate mongo;
    private final ObjectMapper json;
    public Optional<AgentWorkflowCheckpoint> find(String id) {
        return Optional.ofNullable(AgentWorkflowCheckpointInfraMapper.toDomain(mongo.findById(id, AgentWorkflowCheckpointDO.class), json));
    }
    public void save(AgentWorkflowCheckpoint value) {
        var row = AgentWorkflowCheckpointInfraMapper.toDO(value, json);
        try {
            mongo.upsert(Query.query(Criteria.where("id").is(value.runId()).and("status").in("RUNNING", "WAITING")),
                    new Update().set("userId", row.getUserId()).set("status", row.getStatus())
                            .set("stateJson", row.getStateJson()).set("updatedAt", row.getUpdatedAt()), AgentWorkflowCheckpointDO.class);
        } catch (org.springframework.dao.DuplicateKeyException terminal) {
            throw new online.yudream.base.domain.common.exception.BizException("工作流已结束或取消，不能继续推进检查点");
        }
    }
    public void complete(String id) { finish(id, "COMPLETED"); }
    public void cancel(String id) { finish(id, "CANCELLED"); }
    private void finish(String id, String status) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").in("RUNNING", "WAITING")),
                new Update().set("status", status).set("updatedAt", Instant.now()), AgentWorkflowCheckpointDO.class);
    }
}
