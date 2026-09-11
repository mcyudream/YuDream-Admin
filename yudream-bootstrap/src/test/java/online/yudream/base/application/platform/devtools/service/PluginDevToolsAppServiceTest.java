package online.yudream.base.application.platform.devtools.service;

import online.yudream.base.application.platform.agent.dto.AgentTracePageDTO;
import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.application.platform.agent.service.AgentTraceAppService;
import online.yudream.base.application.platform.agent.service.AgentTraceProperties;
import online.yudream.base.application.platform.devtools.cmd.PluginCommandTestCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginDevProjectBatchCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginDevProjectSaveCmd;
import online.yudream.base.application.platform.devtools.cmd.PluginScaffoldCmd;
import online.yudream.base.application.platform.devtools.dto.PluginDevToolsStatusDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceStatus;
import online.yudream.base.domain.platform.agent.valobj.AgentTraceQuery;
import online.yudream.base.domain.platform.plugin.enumerate.PluginDevProjectSource;
import online.yudream.base.domain.platform.plugin.enumerate.PluginDevReloadTrigger;
import online.yudream.base.domain.platform.plugin.event.PluginDevReloadRequested;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectScanResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldSpec;
import online.yudream.base.domain.system.log.repo.SystemLogRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginDevToolsAppServiceTest {

    private final PluginRuntimeGateway runtimeGateway = mock(PluginRuntimeGateway.class);
    private final PluginAppService pluginAppService = mock(PluginAppService.class);
    private final AgentTraceAppService agentTraceAppService = mock(AgentTraceAppService.class);
    private final AgentTraceProperties traceProperties = new AgentTraceProperties();
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
            mock(org.springframework.context.ApplicationEventPublisher.class);
    private final PluginModuleRepo pluginModuleRepo = mock(PluginModuleRepo.class);
    private final SystemLogRepo systemLogRepo = mock(SystemLogRepo.class);
    private final PluginDevToolsAppService service = new PluginDevToolsAppService(
            runtimeGateway, pluginAppService, agentTraceAppService, traceProperties, eventPublisher,
            pluginModuleRepo, systemLogRepo);

    @Test
    void statusAggregatesRuntimeCountsAndSwitches() {
        traceProperties.setEnabled(true);
        when(runtimeGateway.devModeEnabled()).thenReturn(true);
        when(runtimeGateway.devModeProjects()).thenReturn(List.of(
                new PluginDevProjectInfo("demo", "D:/plugins/demo", "D:/plugins/demo/dist", true,
                        PluginDevProjectSource.FILE, true, true, true)));
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
    void requireDevModeRejectsWhenRuntimeGateIsClosed() {
        when(runtimeGateway.devModeEnabled()).thenReturn(false);

        assertThatThrownBy(service::requireDevMode)
                .isInstanceOf(BizException.class)
                .hasMessage("插件开发模式未启用");
    }

    @Test
    void requireDevModePassesWhenRuntimeGateIsOpen() {
        when(runtimeGateway.devModeEnabled()).thenReturn(true);

        service.requireDevMode();
    }

    @Test
    void pluginsMarkDevModeFromGatewayProjects() {
        when(runtimeGateway.devModeProjects()).thenReturn(List.of(
                new PluginDevProjectInfo("demo", "classes", "dist", true,
                        PluginDevProjectSource.CONFIG, true, false, false)));
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
    void addDevProjectRequiresPath() {
        assertThatThrownBy(() -> service.addDevProject(new PluginDevProjectSaveCmd()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件目录不能为空");
    }

    @Test
    void addDevProjectRegistersAndReloadsEnabledPlugin() {
        PluginDevProjectSaveCmd cmd = new PluginDevProjectSaveCmd();
        cmd.setPath("D:/plugins/demo");
        PluginDevProjectInfo saved = new PluginDevProjectInfo("demo", "D:/plugins/demo",
                "D:/plugins/demo/dist", true, PluginDevProjectSource.FILE, true, true, true);
        when(runtimeGateway.registerDevProject(null, "D:/plugins/demo", null, true, null))
                .thenReturn(saved);
        when(runtimeGateway.enabled("demo")).thenReturn(true);

        PluginDevProjectInfo result = service.addDevProject(cmd);

        assertThat(result.code()).isEqualTo("demo");
        assertThat(result.source()).isEqualTo(PluginDevProjectSource.FILE);
        verify(eventPublisher).publishEvent(any(PluginDevReloadRequested.class));
    }

    @Test
    void addDevProjectSkipsReloadWhenPluginNotEnabled() {
        PluginDevProjectSaveCmd cmd = new PluginDevProjectSaveCmd();
        cmd.setPath("D:/plugins/demo");
        cmd.setCode("demo");
        PluginDevProjectInfo saved = new PluginDevProjectInfo("demo", "D:/plugins/demo",
                "D:/plugins/demo/dist", true, PluginDevProjectSource.FILE, true, true, true);
        when(runtimeGateway.registerDevProject("demo", "D:/plugins/demo", null, true, null))
                .thenReturn(saved);
        when(runtimeGateway.enabled("demo")).thenReturn(false);

        service.addDevProject(cmd);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void addDevProjectsRequiresPath() {
        assertThatThrownBy(() -> service.addDevProjects(new PluginDevProjectBatchCmd()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件目录不能为空");
    }

    @Test
    void addDevProjectsRegistersThenReloadsEnabledPlugins() {
        PluginDevProjectBatchCmd cmd = new PluginDevProjectBatchCmd();
        cmd.setPath("D:/plugins");
        PluginDevProjectInfo demo = new PluginDevProjectInfo("demo", "D:/plugins/demo",
                "D:/plugins/demo/dist", true, PluginDevProjectSource.FILE, true, true, true);
        PluginDevProjectInfo store = new PluginDevProjectInfo("store", "D:/plugins/store",
                "D:/plugins/store/dist", true, PluginDevProjectSource.FILE, true, true, true);
        when(runtimeGateway.registerDevProjects("D:/plugins"))
                .thenReturn(new PluginDevProjectScanResult(List.of(demo, store), List.of(
                        new PluginDevProjectScanResult.Skipped("wiki", "D:/plugins/wiki", "已登记"))));
        when(runtimeGateway.enabled("demo")).thenReturn(true);
        when(runtimeGateway.enabled("store")).thenReturn(false);

        PluginDevProjectScanResult result = service.addDevProjects(cmd);

        assertThat(result.registered()).containsExactly(demo, store);
        assertThat(result.skipped()).hasSize(1);
        ArgumentCaptor<PluginDevReloadRequested> captor = ArgumentCaptor.forClass(PluginDevReloadRequested.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().pluginCode()).isEqualTo("demo");
        assertThat(captor.getValue().trigger()).isEqualTo(PluginDevReloadTrigger.REGISTER);
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
        when(agentTraceAppService.detail("missing"))
                .thenThrow(new BizException("执行追踪不存在或已过期：missing"));

        assertThatThrownBy(() -> service.traceDetail("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("执行追踪不存在");
    }

    @Test
    void tracesBuildsPageFromRepository() {
        AgentTracePageDTO expected = AgentTracePageDTO.builder()
                .total(1)
                .page(1)
                .size(20)
                .list(List.of(AgentTraceSummaryDTO.builder()
                        .traceId("t1")
                        .source(AgentTraceSource.PLUGIN)
                        .ownerPluginCode("demo")
                        .agentId("-1")
                        .agentCode("plugin-agent")
                        .agentName("插件代理")
                        .status(AgentTraceStatus.RUNNING)
                        .build()))
                .build();
        when(agentTraceAppService.page(any(AgentTraceQuery.class))).thenReturn(expected);

        AgentTracePageDTO page = service.traces(AgentTraceQuery.of(AgentTraceSource.PLUGIN, "demo", null, 1, 20));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getAgentId()).isEqualTo("-1");
        assertThat(page.getList().getFirst().getStatus()).isEqualTo(AgentTraceStatus.RUNNING);
    }

    @Test
    void disablePreviewRejectsUnknownPlugin() {
        when(pluginAppService.listInstalled()).thenReturn(List.of());

        assertThatThrownBy(() -> service.disablePreview("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("插件不存在");
    }

    @Test
    void disablePreviewComputesTransitiveHardBlockersInDisableOrder() {
        // c 硬依赖 b、b 硬依赖 a；soft 软依赖 a 且已启用；loaded-only 硬依赖 a 但仅加载未启用
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("a").build(),
                PluginModuleDTO.builder().code("b").dependencies(List.of("a")).build(),
                PluginModuleDTO.builder().code("c").dependencies(List.of("b")).build(),
                PluginModuleDTO.builder().code("soft").softDependencies(List.of("a")).build(),
                PluginModuleDTO.builder().code("loaded-only").dependencies(List.of("a")).build(),
                PluginModuleDTO.builder().code("unrelated").build()));
        when(runtimeGateway.enabled(any())).thenReturn(false);
        when(runtimeGateway.enabled("b")).thenReturn(true);
        when(runtimeGateway.enabled("c")).thenReturn(true);
        when(runtimeGateway.enabled("soft")).thenReturn(true);
        when(runtimeGateway.loaded(any())).thenReturn(false);
        when(runtimeGateway.loaded("b")).thenReturn(true);
        when(runtimeGateway.loaded("loaded-only")).thenReturn(true);

        var preview = service.disablePreview("a");

        assertThat(preview.getCode()).isEqualTo("a");
        // 建议禁用顺序：最外层依赖方 c 在前，b 随后；loaded-only 未启用不构成禁用阻塞
        assertThat(preview.getBlockers()).containsExactly("c", "b");
        assertThat(preview.getSoftDependents()).containsExactly("soft");
        assertThat(preview.getUnloadBlockers()).containsExactly("b", "loaded-only");
    }

    @Test
    void disablePreviewReturnsEmptyListsWhenNoDependents() {
        when(pluginAppService.listInstalled()).thenReturn(List.of(
                PluginModuleDTO.builder().code("solo").build()));

        var preview = service.disablePreview("solo");

        assertThat(preview.getBlockers()).isEmpty();
        assertThat(preview.getSoftDependents()).isEmpty();
        assertThat(preview.getUnloadBlockers()).isEmpty();
    }

    @Test
    void scaffoldRejectsInvalidCode() {
        PluginScaffoldCmd cmd = new PluginScaffoldCmd();
        cmd.setParentDir("D:/plugins");
        cmd.setCode("Invalid_Code");

        assertThatThrownBy(() -> service.scaffold(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("kebab-case");
        verify(runtimeGateway, never()).scaffoldPlugin(any());
    }

    @Test
    void scaffoldRejectsSelfDependency() {
        PluginScaffoldCmd cmd = new PluginScaffoldCmd();
        cmd.setParentDir("D:/plugins");
        cmd.setCode("demo");
        cmd.setDepend(List.of("demo"));

        assertThatThrownBy(() -> service.scaffold(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能依赖自身");
    }

    @Test
    void scaffoldGeneratesAndRegistersByDefault() {
        PluginScaffoldCmd cmd = new PluginScaffoldCmd();
        cmd.setParentDir("D:/plugins");
        cmd.setCode("demo-tool");
        PluginScaffoldResult generated = new PluginScaffoldResult("demo-tool", "D:/plugins/yudream-plugin-demo-tool",
                "online.yudream.base.plugin.demotool.bootstrap.DemoToolPlugin", "2.7.0", List.of("pom.xml"));
        when(runtimeGateway.scaffoldPlugin(any(PluginScaffoldSpec.class))).thenReturn(generated);

        var dto = service.scaffold(cmd);

        assertThat(dto.getCode()).isEqualTo("demo-tool");
        assertThat(dto.isRegistered()).isTrue();
        assertThat(dto.getMainClass()).isEqualTo("online.yudream.base.plugin.demotool.bootstrap.DemoToolPlugin");
        // 领域规格负责编码→包名/类名推导：连字符段去横线后首字母大写拼接
        ArgumentCaptor<PluginScaffoldSpec> specCaptor = ArgumentCaptor.forClass(PluginScaffoldSpec.class);
        verify(runtimeGateway).scaffoldPlugin(specCaptor.capture());
        assertThat(specCaptor.getValue().basePackage()).isEqualTo("online.yudream.base.plugin.demotool");
        assertThat(specCaptor.getValue().entryClassName()).isEqualTo("DemoToolPlugin");
        assertThat(specCaptor.getValue().moduleDirName()).isEqualTo("yudream-plugin-demo-tool");
        verify(runtimeGateway).registerDevProject("demo-tool", "D:/plugins/yudream-plugin-demo-tool", null, true, null);
    }

    @Test
    void scaffoldSkipsRegistrationWhenDisabled() {
        PluginScaffoldCmd cmd = new PluginScaffoldCmd();
        cmd.setParentDir("D:/plugins");
        cmd.setCode("demo");
        cmd.setRegister(false);
        PluginScaffoldResult generated = new PluginScaffoldResult("demo", "D:/plugins/yudream-plugin-demo",
                "online.yudream.base.plugin.demo.bootstrap.DemoPlugin", "2.7.0", List.of("pom.xml"));
        when(runtimeGateway.scaffoldPlugin(any(PluginScaffoldSpec.class))).thenReturn(generated);

        var dto = service.scaffold(cmd);

        assertThat(dto.isRegistered()).isFalse();
        verify(runtimeGateway, never()).registerDevProject(any(), any(), any(), any(Boolean.class), any());
    }
}
