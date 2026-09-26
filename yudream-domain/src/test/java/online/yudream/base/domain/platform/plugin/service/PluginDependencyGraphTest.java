package online.yudream.base.domain.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginDependencyGraphTest {

    @Test
    void affectedClosureStopsLeafDependentsFirstAndTargetLast() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("provider", module("provider"));
        // consumer 硬依赖 provider；theme 软依赖 provider 与 consumer
        modules.put("consumer", module("consumer", List.of("provider"), List.of()));
        modules.put("theme", module("theme", List.of(), List.of("provider", "consumer")));
        modules.put("bystander", module("bystander"));

        List<String> order = PluginDependencyGraph.affectedClosure("provider", modules).stream()
                .map(PluginModule::getCode)
                .toList();

        // theme（叶子）最先停机，consumer 次之，provider 最后；无关插件闭包只有自身
        assertEquals(List.of("theme", "consumer", "provider"), order);
        assertEquals(List.of("bystander"), PluginDependencyGraph.affectedClosure("bystander", modules).stream()
                .map(PluginModule::getCode)
                .toList());
    }

    @Test
    void hardDependentClosureLocksOnlyHardPathDependents() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("provider", module("provider"));
        // hardChild 硬依赖 provider；softTheme 仅软依赖 provider
        modules.put("hardChild", module("hardChild", List.of("provider"), List.of()));
        modules.put("softTheme", module("softTheme", List.of(), List.of("provider")));
        // chained 硬依赖 softTheme（软依赖 provider），可随 softTheme 降级恢复
        modules.put("chained", module("chained", List.of("softTheme"), List.of()));

        assertEquals(java.util.Set.of("hardChild"),
                PluginDependencyGraph.hardDependentClosure("provider", modules));

        // 目标自身不出现在闭包里；chained 硬依赖 softTheme（对 provider 仅软依赖）可恢复
        assertEquals(java.util.Set.of("chained"), PluginDependencyGraph.hardDependentClosure("softTheme", modules));
    }

    @Test
    void directDependentCodesListsHardBeforeSoft() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("provider", module("provider"));
        modules.put("soft", module("soft", List.of(), List.of("provider")));
        modules.put("hard", module("hard", List.of("provider"), List.of()));

        assertEquals(List.of("hard", "soft"),
                PluginDependencyGraph.directDependentCodes("provider", modules));
    }

    @Test
    void restoreOrderPutsSoftDependencyProviderBeforeConsumer() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        // 复现 mcpanel/minecraft-server：字母序消费方在前，软依赖必须反超
        modules.put("mcpanel", module("mcpanel", List.of(), List.of("minecraft-server")));
        modules.put("minecraft-server", module("minecraft-server"));

        assertEquals(List.of("minecraft-server", "mcpanel"),
                PluginDependencyGraph.restoreOrder(modules).stream().map(PluginModule::getCode).toList());
    }

    @Test
    void restoreOrderRespectsTransitiveChainsAndAlphabeticalTieBreak() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("wallet", module("wallet"));
        modules.put("panel", module("panel", List.of(), List.of("minecraft-server", "wallet")));
        modules.put("minecraft-server", module("minecraft-server", List.of(), List.of("skin")));
        modules.put("skin", module("skin"));
        modules.put("standalone-b", module("standalone-b"));
        modules.put("standalone-a", module("standalone-a"));

        List<String> order = PluginDependencyGraph.restoreOrder(modules).stream()
                .map(PluginModule::getCode)
                .toList();
        // 提供方最先（深度大者在前），无依赖关系的插件按字母序殿后
        assertEquals(List.of("skin", "minecraft-server", "wallet", "panel", "standalone-a", "standalone-b"), order);
    }

    @Test
    void restoreOrderBreaksCyclesWithoutDroppingModules() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("b", module("b", List.of(), List.of("a")));
        modules.put("a", module("a", List.of(), List.of("b")));
        modules.put("c", module("c"));

        List<String> order = PluginDependencyGraph.restoreOrder(modules).stream()
                .map(PluginModule::getCode)
                .toList();

        // 环内相对顺序受 visiting 兜底影响不保证：只要求不丢模块、环成员在前、无环的 c 殿后
        assertEquals(3, order.size());
        assertEquals(java.util.Set.of("a", "b"), new java.util.HashSet<>(order.subList(0, 2)));
        assertEquals("c", order.get(2));
    }

    @Test
    void restoreOrderIgnoresUnknownDependencyCodes() {
        Map<String, PluginModule> modules = new LinkedHashMap<>();
        modules.put("consumer", module("consumer", List.of("ghost"), List.of("missing-provider")));

        assertEquals(List.of("consumer"),
                PluginDependencyGraph.restoreOrder(modules).stream().map(PluginModule::getCode).toList());
    }

    private PluginModule module(String code) {
        return module(code, List.of(), List.of());
    }

    private PluginModule module(String code, List<String> dependencies, List<String> softDependencies) {
        return PluginModule.builder()
                .code(code)
                .name(code)
                .dependencies(dependencies)
                .softDependencies(softDependencies)
                .build();
    }
}
