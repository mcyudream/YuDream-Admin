package online.yudream.base.domain.platform.ai.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 系统工具模块：归属推导、按需加载状态与全局/模块开关语义。 */
class AiToolModulePlanTest {

    @Test
    void 工具名前缀决定模块归属() {
        assertEquals("cms", AiToolModules.platformToolModule("cms.canvas.patch"));
        assertEquals("web", AiToolModules.platformToolModule("web.fetch"));
        assertEquals("wiki", AiToolModules.platformToolModule("wiki.search"));
        assertEquals(AiToolModules.MODULE_META, AiToolModules.platformToolModule("load_tools"));
        assertEquals(AiToolModules.MODULE_GENERAL, AiToolModules.platformToolModule("plainName"));
        assertEquals("minecraft", AiToolModules.platformToolModule("minecraft.server.status"));
    }

    @Test
    void mcp模块与工具编码一致() {
        assertEquals("mcp:mcpanel", AiToolModules.MCP_PREFIX + "mcpanel");
        assertEquals("mcp.mcpanel.list_containers", AiToolModules.mcpToolName("mcpanel", "list_containers"));
        assertEquals("mcpanel", AiToolModules.mcpServerCode(AiToolModules.mcpModuleName("mcpanel")));
        assertTrue(AiToolModules.mcpModuleCode("mcp:mcpanel"));
        assertFalse(AiToolModules.mcpModuleCode("cms"));
        assertTrue(AiToolModules.pluginModuleCode("plugin:ai-chatbot"));
    }

    @Test
    void 常驻模块默认可见而按需模块需加载() {
        AiToolModulePlan plan = plan(Map.of(
                "wiki", List.of("wiki_search"),
                "cms", List.of("cms_canvas_patch")
        ));
        AiToolLoadState state = plan.newLoadState();
        assertTrue(plan.onDemandActive());
        assertTrue(state.visible("wiki_search"), "常驻模块工具默认可见");
        assertTrue(state.visible("load_tools"), "元工具始终可见");
        assertFalse(state.visible("cms_canvas_patch"), "按需模块工具加载前不可见");

        assertTrue(state.load("cms"));
        assertTrue(state.visible("cms_canvas_patch"), "加载后可见");
        assertTrue(state.loaded("cms"));
        assertFalse(state.load("nope"), "未知模块加载失败");
        assertTrue(state.onDemand(), "存在按需模块时提示模型");
    }

    @Test
    void 恢复续跑不做按需过滤() {
        AiToolModulePlan plan = plan(Map.of("cms", List.of("cms_canvas_patch")));
        AiToolLoadState state = plan.newLoadState(false);
        assertFalse(state.onDemand());
        assertTrue(state.visible("cms_canvas_patch"));
    }

    @Test
    void 模块关闭时工具不进入计划() {
        Map<String, AiToolModules.ModuleMeta> modules = new LinkedHashMap<>();
        modules.put("cms", AiToolModules.platformModule("cms"));
        AiToolModulePlan plan = new AiToolModulePlan(true, true, AiToolModules.LOAD_TOOLS, modules,
                Map.of("cms_canvas_patch", entry("cms_canvas_patch", "cms")),
                Map.of("cms", List.of("cms_canvas_patch")), Set.of("cms"));
        assertFalse(plan.moduleAllowed("cms"));
        assertFalse(plan.toolAllowed("cms_canvas_patch"));
        assertFalse(plan.onDemandActive(), "关闭的模块不构成待加载项");
    }

    @Test
    void 开关聚合的默认与全局语义() {
        AiToolSettings settings = AiToolSettings.defaults();
        assertTrue(settings.isSystemEnabled());
        assertTrue(settings.isModuleEnabled("cms"), "未登记模块默认启用");

        settings.updateModuleEnabled("cms", false);
        assertFalse(settings.isModuleEnabled("cms"));
        assertTrue(settings.isModuleEnabled("wiki"));

        settings.updateSystemEnabled(false);
        assertFalse(settings.isModuleEnabled("wiki"), "总开关关闭时全部不可用");

        assertThrows(BizException.class, () -> settings.updateModuleEnabled(AiToolModules.MODULE_META, false));
    }

    private AiToolModulePlan plan(Map<String, List<String>> moduleTools) {
        Map<String, AiToolModules.ModuleMeta> modules = new LinkedHashMap<>();
        Map<String, AiToolModulePlan.ToolEntry> tools = new LinkedHashMap<>();
        moduleTools.forEach((module, names) -> {
            modules.put(module, AiToolModules.platformModule(module));
            names.forEach(name -> tools.put(name, entry(name, module)));
        });
        modules.putIfAbsent(AiToolModules.MODULE_META, AiToolModules.platformModule(AiToolModules.MODULE_META));
        return new AiToolModulePlan(true, true, AiToolModules.LOAD_TOOLS, modules, tools, moduleTools, Set.of());
    }

    private AiToolModulePlan.ToolEntry entry(String safeName, String module) {
        return new AiToolModulePlan.ToolEntry(safeName, safeName, safeName, "", "", "WRITE", module, false);
    }
}
