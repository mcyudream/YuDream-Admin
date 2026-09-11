package online.yudream.base.application.platform.agent.service;

import online.yudream.base.application.platform.agent.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceStatsDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceBucket;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTraceAppServiceTest {

    private final AgentExecutionTraceRepo traceRepo = mock(AgentExecutionTraceRepo.class);
    private final AgentTraceAppService service = new AgentTraceAppService(traceRepo);

    @Test
    void pageMapsRepositoryRows() {
        AgentExecutionTrace trace = AgentExecutionTrace.start("t1", AgentTraceSource.CHAT, null,
                AgentApplication.builder().id(-1L).code("wiki-bot").name("Wiki").build(), "你好");
        when(traceRepo.count(any(AgentTraceQuery.class))).thenReturn(1L);
        when(traceRepo.query(any(AgentTraceQuery.class))).thenReturn(List.of(trace));

        AgentTracePageDTO page = service.page(AgentTraceQuery.of(AgentTraceSource.CHAT, null, null, 1, 20));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getAgentId()).isEqualTo("-1");
        assertThat(page.getList().getFirst().getStatus()).isEqualTo(AgentTraceStatus.RUNNING);
    }

    @Test
    void detailFailsWhenAbsent() {
        when(traceRepo.findByTraceId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行追踪不存在");
    }

    @Test
    void statsDelegatesToRepository() {
        when(traceRepo.stats(any(AgentTraceQuery.class))).thenReturn(new AgentTraceStats(
                3, 2, 1, 0, 120, 300, 10, 20, 30,
                List.of(new AgentTraceBucket("CHAT", "聊天", 3)),
                List.of()));

        AgentTraceStatsDTO stats = service.stats(AgentTraceQuery.of(null, null, null, 1, 20));

        assertThat(stats.getTotal()).isEqualTo(3);
        assertThat(stats.getSucceeded()).isEqualTo(2);
        assertThat(stats.getFailed()).isEqualTo(1);
        assertThat(stats.getSources()).hasSize(1);
        assertThat(stats.getSources().getFirst().label()).isEqualTo("聊天");
    }

    @Test
    void deleteRequiresExistingTrace() {
        when(traceRepo.deleteByTraceId("gone")).thenReturn(0L);

        assertThatThrownBy(() -> service.delete("gone"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行追踪不存在");
    }

    @Test
    void clearDelegatesToRepository() {
        AgentTraceQuery query = AgentTraceQuery.of(AgentTraceSource.CMS, null, AgentTraceStatus.FAILED, 1, 20);
        when(traceRepo.delete(query)).thenReturn(4L);

        assertThat(service.clear(query)).isEqualTo(4L);
        verify(traceRepo).delete(eq(query));
    }
}
