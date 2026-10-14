package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint;
import java.util.Optional;

/** 保存宿主业务执行语义；不会替代 AgentScope 原生会话日志。 */
public interface AgentWorkflowCheckpointRepo {
    Optional<AgentWorkflowCheckpoint> find(String runId);
    void save(AgentWorkflowCheckpoint checkpoint);
    void complete(String runId);
    void cancel(String runId);
}
