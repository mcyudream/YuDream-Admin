package online.yudream.base.application.platform.mcpserver.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcpserver.service.McpToolSource;
import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerAppServiceTest {

    private static McpExposedTool tool(String module, String name, String permissionCode) {
        return new McpExposedTool(name, module, name, "描述：" + name, permissionCode,
                Map.of("type", "object"), false);
    }

    /** 可编程 fake：模块/工具清单可配，调用记录入列表。 */
    private static final class FakeSource implements McpToolSource {
        List<McpToolSource.McpToolModuleRef> moduleRefs;
        Map<String, List<McpExposedTool>> tools;
        final List<String> calls = new java.util.ArrayList<>();

        FakeSource(List<McpToolSource.McpToolModuleRef> moduleRefs, Map<String, List<McpExposedTool>> tools) {
            this.moduleRefs = moduleRefs;
            this.tools = tools;
        }

        @Override
        public List<McpToolSource.McpToolModuleRef> modules() {
            return moduleRefs;
        }

        @Override
        public List<McpExposedTool> exposedTools(String module) {
            return tools.getOrDefault(module, List.of());
        }

        @Override
        public McpToolOutcome call(String module, String toolName, Map<String, Object> arguments,
                                   McpToolPrincipal principal) {
            calls.add(module + ":" + toolName);
            return new McpToolOutcome(false, "ok:" + module + ":" + toolName);
        }
    }

    private static McpServerAppService service(FakeSource source, boolean enabled) {
        CapabilityAppService capability = new CapabilityAppService(null, List.of()) {
            @Override
            public boolean enabled(String code) {
                return enabled;
            }
        };
        return new McpServerAppService(capability, source);
    }

    private static final McpToolPrincipal ADMIN = new McpToolPrincipal(1L, List.of("*"));
    private static final McpToolPrincipal VIEWER = new McpToolPrincipal(2L, List.of("plugin:mcpanel:view"));

    @Test
    void catalogFiltersByPermission() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板")),
                Map.of("mcpanel", List.of(
                        tool("mcpanel", "mcpanel_list", "plugin:mcpanel:view"),
                        tool("mcpanel", "mcpanel_manage", "plugin:mcpanel:manage"))));
        McpServerAppService svc = service(source, true);

        McpServerAppService.McpCatalog admin = svc.catalog(null, ADMIN.permissions());
        assertEquals(2, admin.tools().size());
        assertEquals(1, admin.moduleSummaries().size());

        McpServerAppService.McpCatalog viewer = svc.catalog(null, VIEWER.permissions());
        assertEquals(1, viewer.tools().size());
        assertEquals("mcpanel_list", viewer.tools().get(0).name());
    }

    @Test
    void catalogModuleScopeAndUnknownModule() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板"), new McpToolSource.McpToolModuleRef("wallet", "钱包")),
                Map.of("mcpanel", List.of(tool("mcpanel", "mcpanel_list", "plugin:mcpanel:view")),
                        "wallet", List.of(tool("wallet", "wallet_balance", ""))));
        McpServerAppService svc = service(source, true);

        assertEquals(1, svc.catalog("mcpanel", ADMIN.permissions()).tools().size());
        assertEquals(1, svc.catalog("plugin:mcpanel", ADMIN.permissions()).tools().size());
        assertTrue(svc.catalog("no-such", ADMIN.permissions()).tools().isEmpty());
    }

    @Test
    void catalogDedupesCollidingNamesAcrossModules() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("aaa", "A"), new McpToolSource.McpToolModuleRef("bbb", "B")),
                Map.of("aaa", List.of(tool("aaa", "shared_tool", "")),
                        "bbb", List.of(tool("bbb", "shared_tool", ""))));
        McpServerAppService svc = service(source, true);

        McpServerAppService.McpCatalog full = svc.catalog(null, ADMIN.permissions());
        assertEquals(1, full.tools().size());
        assertEquals("aaa", full.tools().get(0).module());
        // 撞名工具仍可在各自模块端点使用
        assertEquals(1, svc.catalog("bbb", ADMIN.permissions()).tools().size());
    }

    @Test
    void callToolResolvesOwnerAndChecksPermission() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板")),
                Map.of("mcpanel", List.of(
                        tool("mcpanel", "mcpanel_list", "plugin:mcpanel:view"),
                        tool("mcpanel", "mcpanel_manage", "plugin:mcpanel:manage"))));
        McpServerAppService svc = service(source, true);

        assertEquals("ok:mcpanel:mcpanel_list", svc.callTool(null, "mcpanel_list", Map.of(), VIEWER).text());
        assertEquals(List.of("mcpanel:mcpanel_list"), source.calls);
        // 无权限调用管理工具被拒（归属解析阶段即拒绝，不会触达工具源）
        assertThrows(BizException.class, () -> svc.callTool(null, "mcpanel_manage", Map.of(), VIEWER));
        assertEquals(1, source.calls.size());
        assertThrows(BizException.class, () -> svc.callTool("mcpanel", "no_such", Map.of(), ADMIN));
        assertThrows(BizException.class, () -> svc.callTool("mcpanel", " ", Map.of(), ADMIN));
    }

    @Test
    void disabledCapabilityBlocksEverything() {
        McpServerAppService svc = service(new FakeSource(List.of(), Map.of()), false);
        BizException ex = assertThrows(BizException.class, () -> svc.catalog(null, ADMIN.permissions()));
        assertTrue(ex.getMessage().contains("MCP 服务器"));
        assertThrows(BizException.class, () -> svc.callTool(null, "x", Map.of(), ADMIN));
    }

    @Test
    void adminOverviewReportsEnabledStateAndFullInventory() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板")),
                Map.of("mcpanel", List.of(
                        tool("mcpanel", "mcpanel_list", "plugin:mcpanel:view"),
                        tool("mcpanel", "mcpanel_manage", "plugin:mcpanel:manage"))));
        McpServerAppService svc = service(source, true);

        Map<String, Object> overview = svc.adminOverview();
        assertEquals(Boolean.TRUE, overview.get("enabled"));
        assertEquals("/api/mcp", overview.get("endpointFull"));
        assertEquals(2, ((Number) overview.get("toolCount")).intValue());
        List<Map<String, Object>> modules = (List<Map<String, Object>>) (List<?>) overview.get("modules");
        assertEquals(1, modules.size());
        assertEquals("/api/mcp/mcpanel", modules.get(0).get("endpoint"));
        assertEquals("MC 面板", modules.get(0).get("displayName"));
        assertEquals(2, ((Number) modules.get(0).get("toolCount")).intValue());
    }

    @Test
    void adminOverviewDisabledIsEmpty() {
        McpServerAppService svc = service(new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板")),
                Map.of("mcpanel", List.of(tool("mcpanel", "mcpanel_list", "plugin:mcpanel:view")))), false);
        Map<String, Object> overview = svc.adminOverview();
        assertEquals(Boolean.FALSE, overview.get("enabled"));
        assertEquals(0, ((Number) overview.get("toolCount")).intValue());
        assertTrue(((List<?>) overview.get("modules")).isEmpty());
    }

    @Test
    void nullPrincipalMeansNoPermissions() {
        FakeSource source = new FakeSource(
                List.of(new McpToolSource.McpToolModuleRef("mcpanel", "MC 面板")),
                Map.of("mcpanel", List.of(
                        tool("mcpanel", "mcpanel_open", ""),
                        tool("mcpanel", "mcpanel_view", "plugin:mcpanel:view"))));
        McpServerAppService svc = service(source, true);

        McpServerAppService.McpCatalog catalog = svc.catalog(null, null);
        assertEquals(1, catalog.tools().size());
        assertEquals("mcpanel_open", catalog.tools().get(0).name());
    }
}
