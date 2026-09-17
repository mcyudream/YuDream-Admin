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
