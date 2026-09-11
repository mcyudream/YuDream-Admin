package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStats;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行链路追踪仓储。
 */
public interface AgentExecutionTraceRepo {

    AgentExecutionTrace save(AgentExecutionTrace trace);

    Optional<AgentExecutionTrace> findByTraceId(String traceId);

    List<AgentExecutionTrace> query(AgentTraceQuery query);

    List<AgentExecutionTrace> listForExport(AgentTraceQuery query, int limit);

    long count(AgentTraceQuery query);

    AgentTraceStats stats(AgentTraceQuery query);

    long deleteByTraceId(String traceId);

    long delete(AgentTraceQuery query);
}
