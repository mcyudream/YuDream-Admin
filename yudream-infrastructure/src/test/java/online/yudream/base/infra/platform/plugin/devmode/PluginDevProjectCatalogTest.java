package online.yudream.base.infra.platform.plugin.devmode;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.infra.platform.plugin.service.PluginDevModeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDevProjectCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void addFromDirectoryRegistersNestedModulesAndDeduplicates() throws IOException {
        Path storeFile = tempDir.resolve("dev-projects.json");
        Path repo = tempDir.resolve("yudream-admin-plugins");
        Path pluginA = writePlugin(repo.resolve("yudream-plugins").resolve("yudream-plugin-alpha"), "alpha");
        Path pluginB = writePlugin(repo.resolve("yudream-plugins").resolve("yudream-plugin-beta"), "beta");
        writePlugin(repo.resolve("yudream-plugins").resolve("yudream-plugin-alpha-copy"), "alpha");
        Files.createDirectories(repo.resolve("yudream-frontend").resolve("packages").resolve("plugin-alpha"));
        Files.createDirectories(repo.resolve("node_modules").resolve("ignored-plugin").resolve("src").resolve("main").resolve("resources"));
        Files.writeString(repo.resolve("node_modules").resolve("ignored-plugin")
                .resolve("src").resolve("main").resolve("resources").resolve("plugin.yml"),
                "name: ignored\nversion: 1.0.0\nmain: x.Ignored\n", StandardCharsets.UTF_8);

        PluginDevModeProperties properties = new PluginDevModeProperties();
        properties.setStoreFile(storeFile.toString());
        PluginDevModeProperties.DevProject existing = new PluginDevModeProperties.DevProject();
        existing.setCode("beta");
        existing.setPath(pluginB.toString());
        properties.setProjects(java.util.List.of(existing));
        PluginDevProjectCatalog catalog = new PluginDevProjectCatalog(properties, new ObjectMapper());

        PluginDevProjectCatalog.CatalogBatchResult first = catalog.addFromDirectory(repo);
        assertEquals(1, first.registered().size());
        assertEquals("alpha", first.registered().getFirst().getCode());
        assertEquals(pluginA.toAbsolutePath().normalize().toString(), first.registered().getFirst().getPath());
        assertTrue(first.skipped().stream().anyMatch(item ->
                "alpha".equals(item.code()) && "编码冲突".equals(item.reason())));
        assertTrue(first.skipped().stream().anyMatch(item ->
                "beta".equals(item.code()) && "已在配置文件登记".equals(item.reason())));
        assertTrue(first.skipped().stream().noneMatch(item -> "ignored".equals(item.code())));

        PluginDevProjectCatalog.CatalogBatchResult second = catalog.addFromDirectory(repo);
        assertTrue(second.registered().isEmpty());
        assertTrue(second.skipped().stream().anyMatch(item ->
                "alpha".equals(item.code()) && "已登记".equals(item.reason())));
        assertEquals(1, catalog.projects().stream()
                .filter(entry -> "alpha".equals(entry.project().getCode()))
                .count());
    }

    @Test
    void addFromDirectoryRegistersSelectedPluginDirectoryItself() throws IOException {
        Path storeFile = tempDir.resolve("dev-projects.json");
        Path plugin = writePlugin(tempDir.resolve("yudream-plugin-solo"), "solo");
        PluginDevModeProperties properties = new PluginDevModeProperties();
        properties.setStoreFile(storeFile.toString());
        PluginDevProjectCatalog catalog = new PluginDevProjectCatalog(properties, new ObjectMapper());

        PluginDevProjectCatalog.CatalogBatchResult result = catalog.addFromDirectory(plugin);
        assertEquals(1, result.registered().size());
        assertEquals("solo", result.registered().getFirst().getCode());
        assertTrue(result.skipped().isEmpty());
    }

    private Path writePlugin(Path root, String code) throws IOException {
        Path resources = root.resolve("src").resolve("main").resolve("resources");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("plugin.yml"),
                "name: " + code + "\nversion: 1.0.0\nmain: online.yudream.base.plugin." + code + ".bootstrap.Plugin\n",
                StandardCharsets.UTF_8);
        return root.toAbsolutePath().normalize();
    }
}
