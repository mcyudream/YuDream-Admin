package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.ai.dto.AiToolCatalogRowDTO;
import online.yudream.base.application.platform.ai.query.AiToolCatalogPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一工具目录：系统 / 插件 / MCP / Python 工具合并一张表，
 * 来源、模块、状态标记与筛选分页一致。
 */
class AiToolModuleCatalogTest {

    private final ToolTestDoubles.StubCapabilityAppService capability = new ToolTestDoubles.StubCapabilityAppService();
    private final ToolTestDoubles.MemoryMcpServerRepo mcpRepo = new ToolTestDoubles.MemoryMcpServerRepo();
    private final ToolTestDoubles.StubAgentToolRepo toolRepo = new ToolTestDoubles.StubAgentToolRepo();
    private final ToolTestDoubles.MemoryToolSettingsRepo settingsRepo = new ToolTestDoubles.MemoryToolSettingsRepo();
    private final ToolTestDoubles.RecordingAiTool fetch = new ToolTestDoubles.RecordingAiTool(
            "web.fetch", "网页抓取", "platform:ai:tool:web-fetch", AgentToolRisk.READ, "web");
    private final ToolTestDoubles.RecordingAiTool canvas = new ToolTestDoubles.RecordingAiTool(
            "cms.canvas.patch", "画布补丁", "platform:ai:tool:cms-canvas-patch", AgentToolRisk.WRITE, "cms");

    private AiToolModuleAppService service() {
        ToolTestDoubles.AgentPluginToolGatewayStub plugin = new ToolTestDoubles.AgentPluginToolGatewayStub();
        plugin.add("mcpanel.list_containers", "容器列表", "mcpanel");
        mcpRepo.save(ToolTestDoubles.mcpServer("stub", "本地桩", true, false, "panel_overview"));
        toolRepo.save(AgentTool.python("风险评分", "risk_score", "def run(params: dict) -> dict:\n    return {}\n"));
        return ToolTestDoubles.moduleService(
                List.of(fetch, canvas), mcpRepo, toolRepo, settingsRepo, capability, List.of(plugin),
                new ToolTestDoubles.StubMcpProvider(List.of()));
    }

    @Test
    void 四种来源合并成同构行() {
        List<AiToolCatalogRowDTO> rows = service().catalogRows();

        assertEquals(5, rows.size(), rows.stream().map(AiToolCatalogRowDTO::code).toList().toString());
        assertTrue(rows.stream().anyMatch(row -> "system".equals(row.source()) && "web.fetch".equals(row.code())));
        assertTrue(rows.stream().anyMatch(row -> "plugin".equals(row.source())
                && "mcpanel.list_containers".equals(row.code()) && "plugin:mcpanel".equals(row.moduleCode())));
        assertTrue(rows.stream().anyMatch(row -> "mcp".equals(row.source())
                && "mcp.stub.panel_overview".equals(row.code()) && "mcp:stub".equals(row.moduleCode())));
        assertTrue(rows.stream().anyMatch(row -> "python".equals(row.source())
                && "risk_score".equals(row.code()) && "custom".equals(row.moduleCode())));
    }

    @Test
    void 可编辑与可删除只给Python工具() {
        List<AiToolCatalogRowDTO> rows = service().catalogRows();

        AiToolCatalogRowDTO python = row(rows, "risk_score");
        assertTrue(python.editable() && python.deletable() && python.testable());
        AiToolCatalogRowDTO system = row(rows, "web.fetch");
        assertFalse(system.editable() || system.deletable());
        assertTrue(system.testable());
        assertEquals("READ", system.risk());
    }

    @Test
    void 写入工具标记需要确认() {
        List<AiToolCatalogRowDTO> rows = service().catalogRows();
        AiToolCatalogRowDTO canvasRow = row(rows, "cms.canvas.patch");
        assertEquals("WRITE", canvasRow.risk());
        assertTrue(canvasRow.requiresConfirmation());
        assertTrue(canvasRow.moduleAlwaysOn() == false, "站点画布按需加载");
    }

    @Test
    void 关键词来源模块状态筛选与分页() {
        AiToolModuleAppService service = service();

        PageResult<AiToolCatalogRowDTO> byKeyword = service.pageCatalog(query(q -> q.setKeyword("画布")));
        assertEquals(1, byKeyword.getTotal());
        assertEquals("cms.canvas.patch", byKeyword.getRecords().get(0).code());

        PageResult<AiToolCatalogRowDTO> bySource = service.pageCatalog(query(q -> q.setSource("mcp")));
        assertEquals(1, bySource.getTotal());
        assertEquals("mcp.stub.panel_overview", bySource.getRecords().get(0).code());

        PageResult<AiToolCatalogRowDTO> byModule = service.pageCatalog(query(q -> q.setModuleCode("plugin:mcpanel")));
        assertEquals(1, byModule.getTotal());

        PageResult<AiToolCatalogRowDTO> page = service.pageCatalog(query(q -> {
            q.setPage(2);
            q.setSize(3);
        }));
        assertEquals(5, page.getTotal());
        assertEquals(2, page.getRecords().size(), "共 5 条、每页 3 条，第二页剩 2 条");
    }

    @Test
    void 模块关闭时行仍在但标记不可用() {
        settingsRepo.save(AiToolSettings.of(true, Map.of("cms", false)));
        AiToolModuleAppService service = service();

        AiToolCatalogRowDTO canvasRow = row(service.catalogRows(), "cms.canvas.patch");
        assertFalse(canvasRow.available());
        assertFalse(canvasRow.moduleEnabled());

        PageResult<AiToolCatalogRowDTO> closed = service.pageCatalog(query(q -> q.setStatus("MODULE_CLOSED")));
        assertTrue(closed.getRecords().stream().anyMatch(row -> "cms.canvas.patch".equals(row.code())));

        PageResult<AiToolCatalogRowDTO> enabled = service.pageCatalog(query(q -> q.setStatus("ENABLED")));
        assertFalse(enabled.getRecords().stream().anyMatch(row -> "cms.canvas.patch".equals(row.code())));
    }

    @Test
    void 总开关关闭时系统与插件工具都标记不可用() {
        settingsRepo.save(AiToolSettings.of(false, Map.of()));
        List<AiToolCatalogRowDTO> rows = service().catalogRows();

        assertFalse(row(rows, "web.fetch").moduleEnabled());
        assertFalse(row(rows, "mcpanel.list_containers").moduleEnabled());
        assertFalse(row(rows, "risk_score").available());
    }

    private AiToolCatalogRowDTO row(List<AiToolCatalogRowDTO> rows, String code) {
        return rows.stream().filter(row -> code.equals(row.code())).findFirst()
                .orElseThrow(() -> new AssertionError("缺少工具行：" + code));
    }

    private AiToolCatalogPageQuery query(java.util.function.Consumer<AiToolCatalogPageQuery> customizer) {
        AiToolCatalogPageQuery query = new AiToolCatalogPageQuery();
        query.setPage(1);
        query.setSize(20);
        customizer.accept(query);
        return query;
    }
}
