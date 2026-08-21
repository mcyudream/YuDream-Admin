package online.yudream.base.infra.platform.plugin.devmode;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginScaffoldGeneratorTest {

    private final PluginScaffoldGenerator generator = new PluginScaffoldGenerator();

    @TempDir
    Path tempDir;

    @Test
    void generatesMinimalModuleSkeleton() throws IOException {
        PluginScaffoldSpec spec = PluginScaffoldSpec.of("demo-tool", "演示工具", "演示 \"引用\"\n换行收敛",
                null, null, tempDir.toString(), List.of("wallet"), List.of("wordle"));

        PluginScaffoldResult result = generator.generate(spec);

        Path root = tempDir.resolve("yudream-plugin-demo-tool");
        assertThat(result.projectPath()).isEqualTo(root.toString());
        assertThat(result.mainClass()).isEqualTo("online.yudream.base.plugin.demotool.bootstrap.DemoToolPlugin");
        assertThat(result.spiVersion()).isEqualTo(PluginScaffoldGenerator.DEFAULT_SPI_VERSION);
        assertThat(result.files()).containsExactlyInAnyOrder(
                "pom.xml", "README.md", "src/main/resources/plugin.yml",
                "src/main/java/online/yudream/base/plugin/demotool/bootstrap/DemoToolPlugin.java");

        String pom = Files.readString(root.resolve("pom.xml"));
        assertThat(pom).contains("<artifactId>yudream-plugin-demo-tool</artifactId>");
        assertThat(pom).contains("<version>1.0.0</version>");
        assertThat(pom).contains("yudream-plugin-spi");
        assertThat(pom).contains(PluginScaffoldGenerator.DEFAULT_SPI_VERSION);

        String pluginYaml = Files.readString(root.resolve("src/main/resources/plugin.yml"));
        assertThat(pluginYaml).contains("name: demo-tool");
        assertThat(pluginYaml).contains("displayName: \"演示工具\"");
        assertThat(pluginYaml).contains("main: online.yudream.base.plugin.demotool.bootstrap.DemoToolPlugin");
        assertThat(pluginYaml).contains("depend:\n  - wallet\n");
        assertThat(pluginYaml).contains("softdepend:\n  - wordle\n");
        // 描述中的引号与换行须转义/收敛，保证 YAML 与 Java 注解均合法
        assertThat(pluginYaml).contains("演示 \\\"引用\\\" 换行收敛");

        String entry = Files.readString(
                root.resolve("src/main/java/online/yudream/base/plugin/demotool/bootstrap/DemoToolPlugin.java"));
        assertThat(entry).contains("package online.yudream.base.plugin.demotool.bootstrap;");
        assertThat(entry).contains("public class DemoToolPlugin implements YuDreamPlugin");
        assertThat(entry).contains("CODE = \"demo-tool\"");
        assertThat(entry).doesNotContain("{{");

        for (String dir : List.of("domain", "application", "infrastructure", "interfaces")) {
            assertThat(root.resolve("src/main/java/online/yudream/base/plugin/demotool").resolve(dir))
                    .isDirectory();
        }
    }

    @Test
    void honorsExplicitSpiVersionAndDefaultsDisplayName() throws IOException {
        PluginScaffoldSpec spec = PluginScaffoldSpec.of("demo", null, null, "0.1.0", "9.9.9",
                tempDir.toString(), null, null);

        PluginScaffoldResult result = generator.generate(spec);

        assertThat(result.spiVersion()).isEqualTo("9.9.9");
        Path root = tempDir.resolve("yudream-plugin-demo");
        String pluginYaml = Files.readString(root.resolve("src/main/resources/plugin.yml"));
        assertThat(pluginYaml).contains("version: 0.1.0");
        assertThat(pluginYaml).contains("displayName: \"demo\"");
        assertThat(pluginYaml).doesNotContain("depend");
        String pom = Files.readString(root.resolve("pom.xml"));
        assertThat(pom).contains("<version>9.9.9</version>");
    }

    @Test
    void rejectsNonEmptyTargetDirectory() throws IOException {
        Path existing = tempDir.resolve("yudream-plugin-demo");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("pom.xml"), "x");
        PluginScaffoldSpec spec = PluginScaffoldSpec.of("demo", null, null, null, null,
                tempDir.toString(), null, null);

        assertThatThrownBy(() -> generator.generate(spec))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在且非空");
    }

    @Test
    void rejectsMissingParentDirectory() {
        PluginScaffoldSpec spec = PluginScaffoldSpec.of("demo", null, null, null, null,
                tempDir.resolve("missing").toString(), null, null);

        assertThatThrownBy(() -> generator.generate(spec))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标父目录不存在");
    }
}
