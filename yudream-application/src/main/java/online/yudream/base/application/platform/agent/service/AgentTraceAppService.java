package online.yudream.base.application.platform.agent.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentAssembler;
import online.yudream.base.application.platform.agent.dto.AgentTraceDetailDTO;
import online.yudream.base.application.platform.agent.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceStatsDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Agent 执行流查询与分析：供日志页与开发者工具共用，不依赖开发模式开关。
 */
@Service
@RequiredArgsConstructor
public class AgentTraceAppService {

    private static final int EXPORT_LIMIT = 10_000;

    private final AgentExecutionTraceRepo traceRepo;

    public AgentTracePageDTO page(AgentTraceQuery query) {
        AgentTraceQuery safe = query == null ? AgentTraceQuery.of(null, null, null, 1, 20) : query;
        return AgentAssembler.toTracePage(safe, traceRepo.count(safe), traceRepo.query(safe));
    }

    public AgentTraceDetailDTO detail(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw new BizException("追踪 ID 不能为空");
        }
        return traceRepo.findByTraceId(traceId.trim())
                .map(AgentAssembler::toTraceDetail)
                .orElseThrow(() -> new BizException("执行追踪不存在或已过期：" + traceId));
    }

    public AgentTraceStatsDTO stats(AgentTraceQuery query) {
        AgentTraceQuery safe = query == null ? AgentTraceQuery.of(null, null, null, 1, 20) : query;
        return AgentAssembler.toTraceStats(traceRepo.stats(safe));
    }

    public List<AgentTraceSummaryDTO> export(AgentTraceQuery query) {
        AgentTraceQuery safe = query == null ? AgentTraceQuery.of(null, null, null, 1, EXPORT_LIMIT) : query;
        return traceRepo.listForExport(safe, EXPORT_LIMIT).stream().map(AgentAssembler::toTraceSummary).toList();
    }

    public long delete(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw new BizException("追踪 ID 不能为空");
        }
        long deleted = traceRepo.deleteByTraceId(traceId.trim());
        if (deleted <= 0) {
            throw new BizException("执行追踪不存在或已过期：" + traceId);
        }
        return deleted;
    }

    public long clear(AgentTraceQuery query) {
        AgentTraceQuery safe = query == null ? AgentTraceQuery.of(null, null, null, 1, 20) : query;
        return traceRepo.delete(safe);
    }
}
