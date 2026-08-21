package online.yudream.base.domain.platform.plugin.valobj;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRuntimeAssetsDiffTest {

    private static PluginRuntimeAssets assets(List<PluginCommandInfo> commands, List<PluginHttpEndpointInfo> endpoints) {
        return new PluginRuntimeAssets("demo", true, true, null, null, null, null, null, endpoints, commands,
                null, null, null, List.of("demo.api"));
    }

    private static PluginCommandInfo command(String command) {
        return new PluginCommandInfo("demo", command, command, command, null, null, true);
    }

    private static PluginHttpEndpointInfo endpoint(String method, String path) {
        return new PluginHttpEndpointInfo("demo", method, path, "/api/plugins/demo" + path, null, true);
    }

    @Test
    void diffCollectsAddedAndRemovedPerCategory() {
        PluginRuntimeAssets before = assets(List.of(command("/old"), command("/keep")),
                List.of(endpoint("GET", "/list")));
        PluginRuntimeAssets after = assets(List.of(command("/keep"), command("/new")), List.of());

        PluginRuntimeAssetsDiff diff = PluginRuntimeAssetsDiff.diff(before, after);

        assertEquals(2, diff.entries().size());
        PluginRuntimeAssetsDiff.Entry commands = diff.entries().stream()
                .filter(entry -> entry.category().equals("commands")).findFirst().orElseThrow();
        assertEquals(List.of("/new"), commands.added());
        assertEquals(List.of("/old"), commands.removed());
        PluginRuntimeAssetsDiff.Entry endpoints = diff.entries().stream()
                .filter(entry -> entry.category().equals("httpEndpoints")).findFirst().orElseThrow();
        assertEquals(List.of(), endpoints.added());
        assertEquals(List.of("GET /api/plugins/demo/list"), endpoints.removed());
    }

    @Test
    void identicalSnapshotsProduceEmptyDiff() {
        PluginRuntimeAssets snapshot = assets(List.of(command("/ping")), List.of(endpoint("GET", "/ping")));
        PluginRuntimeAssetsDiff diff = PluginRuntimeAssetsDiff.diff(snapshot, snapshot);
        assertTrue(diff.isEmpty());
    }
}
