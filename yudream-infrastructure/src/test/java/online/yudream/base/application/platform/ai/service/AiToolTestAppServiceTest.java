package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.cmd.AiToolTestCmd;
import online.yudream.base.application.platform.ai.dto.AiToolTestResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端工具测试：宿主工具、Python 工具与 MCP 工具都能真实调用一次，
 * 且模块开关、工具启用状态与工具权限码照旧复核。
 */
class AiToolTestAppServiceTest {

    private final ToolTestDoubles.StubCapabilityAppService capability = new ToolTestDoubles.StubCapabilityAppService();
    private final ToolTestDoubles.MemoryMcpServerRepo mcpRepo = new ToolTestDoubles.MemoryMcpServerRepo();
    private final ToolTestDoubles.StubAgentToolRepo toolRepo = new ToolTestDoubles.StubAgentToolRepo();
    private final ToolTestDoubles.MemoryToolSettingsRepo settingsRepo = new ToolTestDoubles.MemoryToolSettingsRepo();
    private final ToolTestDoubles.RecordingAiTool fetch = new ToolTestDoubles.RecordingAiTool(
            "web.fetch", "网页抓取", "platform:ai:tool:web-fetch", AgentToolRisk.READ, "web");
    private final ToolTestDoubles.RecordingAiTool canvas = new ToolTestDoubles.RecordingAiTool(
            "cms.canvas.patch", "画布补丁", "platform:ai:tool:cms-canvas-patch", AgentToolRisk.WRITE, "cms");

    private AiToolTestAppService service(List<String> granted) {
        AiToolModuleAppService modules = ToolTestDoubles.moduleService(
                List.of(fetch, canvas), mcpRepo, toolRepo, settingsRepo, capability);
        return new AiToolTestAppService(
                modules,
                toolRepo,
                ToolTestDoubles.permissionGateway(granted),
                capability,
                new ToolTestDoubles.FakeObjectProvider<>(List.of(ToolTestDoubles.recordingRuntime())),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()),
                new ToolTestDoubles.FakeObjectProvider<>(List.of())
        );
    }

    @Test
    void 系统工具测试真实执行并返回载荷与耗时() {
        AiToolTestResultDTO result = service(List.of("platform:ai:tool:web-fetch"))
                .test(cmd("web.fetch", Map.of("url", "https://example.com"), List.of("platform:ai:tool:web-fetch")));

        assertTrue(result.success(), result.message());
        assertEquals("system", result.source());
        assertEquals(1, fetch.calls);
        assertEquals("https://example.com", fetch.lastArguments.get("url"));
        assertEquals("https://example.com", result.payload().get("url"));
        assertTrue(result.durationMillis() >= 0);
    }

    @Test
    void 缺少工具权限码时测试失败且不执行() {
        AiToolTestResultDTO result = service(List.of()).test(cmd("web.fetch", Map.of()));

        assertFalse(result.success());
        assertTrue(result.message().contains("无权调用"), result.message());
        assertEquals(0, fetch.calls, "权限不足不得真实执行");
    }

    @Test
    void 模块关闭时系统工具测试失败() {
        settingsRepo.save(online.yudream.base.domain.platform.ai.aggregate.AiToolSettings
                .of(true, Map.of("web", false)));
        AiToolTestResultDTO result = service(List.of("platform:ai:tool:web-fetch"))
                .test(cmd("web.fetch", Map.of(), List.of("platform:ai:tool:web-fetch")));

        assertFalse(result.success());
        assertTrue(result.message().contains("模块已关闭"), result.message());
        assertEquals(0, fetch.calls);
    }

    @Test
    void 工具不存在时拒绝测试() {
        assertThrows(BizException.class, () -> service(List.of()).test(cmd("nope.missing", Map.of())));
    }

    @Test
    void 插件工具未注册时给出可诊断原因() {
        AiToolModuleAppService modules = ToolTestDoubles.moduleService(
                List.of(fetch, canvas), mcpRepo, toolRepo, settingsRepo, capability);
        AiToolTestAppService service = new AiToolTestAppService(
                modules, toolRepo, ToolTestDoubles.permissionGateway(List.of()), capability,
                new ToolTestDoubles.FakeObjectProvider<>(List.of(ToolTestDoubles.recordingRuntime())),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()));

        // 目录里没有该插件工具 → 直接判不存在
        assertThrows(BizException.class, () -> service.test(cmd("plugin.tool.missing", Map.of())));
    }

    @Test
    void MCP工具测试经提供方执行() {
        mcpRepo.save(ToolTestDoubles.mcpServer("stub", "本地桩", true, false, "panel_overview"));
        AiAgentTool mcpTool = new ToolTestDoubles.RecordingAiTool(
                "mcp.stub.panel_overview", "面板概览", "platform:mcp:server:use", AgentToolRisk.READ, "mcp:stub");
        AiToolModuleAppService modules = ToolTestDoubles.moduleService(
                List.of(fetch, canvas), mcpRepo, toolRepo, settingsRepo, capability, List.of(),
                new ToolTestDoubles.StubMcpProvider(List.of(mcpTool)));
        AiToolTestAppService service = new AiToolTestAppService(
                modules, toolRepo, ToolTestDoubles.permissionGateway(List.of("platform:mcp:server:use")), capability,
                new ToolTestDoubles.FakeObjectProvider<>(List.of(ToolTestDoubles.recordingRuntime())),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()));

        AiToolTestResultDTO result = service.test(cmd("mcp.stub.panel_overview", Map.of("node", "local"),
                List.of("platform:mcp:server:use")));

        assertTrue(result.success(), result.message());
        assertEquals("mcp", result.source());
        assertEquals("local", result.payload().get("node"));
    }

    @Test
    void MCP工具无调用权限时失败() {
        mcpRepo.save(ToolTestDoubles.mcpServer("stub", "本地桩", true, false, "panel_overview"));
        AiAgentTool mcpTool = new ToolTestDoubles.RecordingAiTool(
                "mcp.stub.panel_overview", "面板概览", "platform:mcp:server:use", AgentToolRisk.READ, "mcp:stub");
        AiToolModuleAppService modules = ToolTestDoubles.moduleService(
                List.of(fetch, canvas), mcpRepo, toolRepo, settingsRepo, capability, List.of(),
                new ToolTestDoubles.StubMcpProvider(List.of(mcpTool)));
        AiToolTestAppService service = new AiToolTestAppService(
                modules, toolRepo, ToolTestDoubles.permissionGateway(List.of()), capability,
                new ToolTestDoubles.FakeObjectProvider<>(List.of(ToolTestDoubles.recordingRuntime())),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()),
                new ToolTestDoubles.FakeObjectProvider<>(List.of()));

        AiToolTestResultDTO result = service.test(cmd("mcp.stub.panel_overview", Map.of()));
        assertFalse(result.success());
        assertTrue(result.message().contains("无权调用"), result.message());
    }

    @Test
    void Python工具经运行器执行并可被停用() {
        AgentTool python = AgentTool.python("风险评分", "risk_score",
                "def run(params: dict) -> dict:\n    return {\"score\": 1}\n");
        toolRepo.save(python);

        AiToolTestResultDTO ok = service(List.of()).test(cmd("risk_score", Map.of("score", 95)));
        assertTrue(ok.success(), ok.message());
        assertEquals("python", ok.source());
        assertTrue(String.valueOf(ok.payload()).contains("95"), String.valueOf(ok.payload()));

        python.update("风险评分", "risk_score", "停用后的工具", online.yudream.base.domain.platform.agent.enumerate.AgentToolType.PYTHON,
                "{\"type\":\"object\"}", "{}", "def run(params: dict) -> dict:\n    return {}\n", 10000, null, false);
        toolRepo.save(python);
        AiToolTestResultDTO disabled = service(List.of()).test(cmd("risk_score", Map.of()));
        assertFalse(disabled.success());
        assertTrue(disabled.message().contains("已停用"), disabled.message());
    }

    @Test
    void Python工具依赖集成能力() {
        toolRepo.save(AgentTool.python("风险评分", "risk_score", "def run(params: dict) -> dict:\n    return {}\n"));
        capability.deny = "integration";

        AiToolTestResultDTO result = service(List.of()).test(cmd("risk_score", Map.of()));
        assertFalse(result.success());
        assertTrue(result.message().contains("Python 工具运行") && result.message().contains("未启用"), result.message());
    }

    @Test
    void 工具抛业务异常时返回失败而不是500() {
        fetch.failure = new BizException("web.fetch 缺少 url 参数");
        AiToolTestResultDTO result = service(List.of("platform:ai:tool:web-fetch"))
                .test(cmd("web.fetch", Map.of(), List.of("platform:ai:tool:web-fetch")));

        assertFalse(result.success());
        assertEquals("web.fetch 缺少 url 参数", result.message());
        fetch.failure = null;
    }

    /** 测试命令：权限快照由接口层按当前登录用户捕获，这里显式传入被测用户的授权码。 */
    private AiToolTestCmd cmd(String code, Map<String, Object> arguments) {
        return cmd(code, arguments, List.of());
    }

    private AiToolTestCmd cmd(String code, Map<String, Object> arguments, List<String> granted) {
        Map<String, Object> safe = arguments == null ? new LinkedHashMap<>() : new LinkedHashMap<>(arguments);
        return AiToolTestCmd.builder().code(code).arguments(safe)
                .permissionCodes(new java.util.ArrayList<>(granted)).build();
    }
}
