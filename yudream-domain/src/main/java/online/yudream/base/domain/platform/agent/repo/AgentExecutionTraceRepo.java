package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行链路追踪仓储。
 */
public interface AgentExecutionTraceRepo {

    AgentExecutionTrace save(AgentExecutionTrace trace);

    Optional<AgentExecutionTrace> findByTraceId(String traceId);

    List<AgentExecutionTrace> query(AgentTraceQuery query);

    long count(AgentTraceQuery query);
}
