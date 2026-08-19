package online.yudream.base.application.platform.devtools.service;

import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.application.platform.devtools.cmd.PluginCommandTestCmd;
import online.yudream.base.application.platform.devtools.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.devtools.dto.PluginDevToolsStatusDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentExecutionTrace;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.repo.AgentExecutionTraceRepo;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginDevToolsAppServiceTest {

    private final PluginRuntimeGateway runtimeGateway = mock(PluginRuntimeGateway.class);
    private final PluginAppService pluginAppService = mock(PluginAppService.class);
    private final AgentExecutionTraceRepo traceRepo = mock(AgentExecutionTraceRepo.class);
    private final AgentTraceProperties traceProperties = new AgentTraceProperties();
    private final PluginDevToolsAppService service = new PluginDevToolsAppService(
            runtimeGateway, pluginAppService, traceRepo, traceProperties);

    @Test
    void statusAggregatesRuntimeCountsAndSwitches() {
        traceProperties.setEnabled(true);
        when(runtimeGateway.devModeEnabled()).thenReturn(true);
        when(runtimeGateway.devModeProjects()).thenReturn(List.of(
                new PluginDevProjectInfo("demo", "D:/plugins/demo/target/classes", "D:/plugins/demo/dist", true)));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").loaded(true).enabled(true).build(),
                PluginModuleDTO.builder().code("store").loaded(true).enabled(false).build()));

        PluginDevToolsStatusDTO status = service.status();

        assertThat(status.isDevModeEnabled()).isTrue();
        assertThat(status.isTraceEnabled()).isTrue();
        assertThat(status.getDevProjects()).hasSize(1);
        assertThat(status.getInstalledCount()).isEqualTo(2);
        assertThat(status.getLoadedCount()).isEqualTo(2);
        assertThat(status.getEnabledCount()).isEqualTo(1);
    }

    @Test
    void pluginsMarkDevModeFromGatewayProjects() {
        when(runtimeGateway.devModeProjects()).thenReturn(List.of(
                new PluginDevProjectInfo("demo", "classes", "dist", true)));
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("demo").name("演示").build(),
                PluginModuleDTO.builder().code("store").name("商店").build()));

        var plugins = service.plugins();

        assertThat(plugins).hasSize(2);
        assertThat(plugins.get(0).isDevMode()).isTrue();
        assertThat(plugins.get(0).getDevProject().code()).isEqualTo("demo");
        assertThat(plugins.get(1).isDevMode()).isFalse();
        assertThat(plugins.get(1).getDevProject()).isNull();
    }

    @Test
    void commandTestRequiresCommandName() {
        assertThatThrownBy(() -> service.commandTest("demo", new PluginCommandTestCmd()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("指令名不能为空");
    }

    @Test
    void commandTestDelegatesToRuntimeGateway() {
        PluginCommandTestCmd cmd = new PluginCommandTestCmd();
        cmd.setCommand("抽签");
        cmd.setArguments(List.of("a", "b"));
        when(runtimeGateway.testCommand("demo", "抽签", List.of("a", "b"), null))
                .thenReturn(PluginCommandTestResult.succeeded("demo", "抽签", 5L));

        PluginCommandTestResult result = service.commandTest("demo", cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.matched()).isTrue();
    }

    @Test
    void traceDetailFailsWhenAbsent() {
        when(traceRepo.findByTraceId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.traceDetail("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行追踪不存在");
    }

    @Test
    void tracesBuildsPageFromRepository() {
        AgentExecutionTrace trace = AgentExecutionTrace.start("t1", AgentTraceSource.PLUGIN, "demo",
                AgentApplication.builder().id(-1L).code("plugin-agent").name("插件代理").build(), "输入");
        when(traceRepo.count(any(AgentTraceQuery.class))).thenReturn(1L);
        when(traceRepo.query(any(AgentTraceQuery.class))).thenReturn(List.of(trace));

        AgentTracePageDTO page = service.traces(AgentTraceQuery.of(AgentTraceSource.PLUGIN, "demo", null, 1, 20));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getAgentId()).isEqualTo("-1");
        assertThat(page.getList().getFirst().getStatus()).isEqualTo(AgentTraceStatus.RUNNING);
    }
}
