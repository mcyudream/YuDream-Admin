package online.yudream.base.infra.platform.plugin.devmode;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldSpec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 插件脚手架生成器：按官方插件仓最小骨架在宿主机生成 Maven 模块
 * （pom.xml、plugin.yml、入口类与 domain/application/infrastructure/interfaces 分包目录）。
 * 模板为 classpath 资源（devtools/scaffold/*.tpl），以 {{TOKEN}} 占位替换；
 * 只写文件，不编译、不登记（登记由应用层另行调用运行时网关）。
 */
@Slf4j
@Component
public class PluginScaffoldGenerator {

    /**
     * 生成 pom.xml 的默认 SPI 依赖版本，与宿主根 pom 的 yudream.plugin.spi.version 保持一致
     * （SPI 升版时同步更新）；仅作默认值，请求可显式覆盖，生成后也可手改。
     */
    static final String DEFAULT_SPI_VERSION = "2.7.0";

    private static final String TEMPLATE_DIR = "devtools/scaffold/";
    /** 按职责分包的固定空目录（bootstrap 由入口类写入时创建） */
    private static final List<String> PACKAGE_DIRS = List.of("domain", "application", "infrastructure", "interfaces");

    public PluginScaffoldResult generate(PluginScaffoldSpec spec) {
        Path parent = Path.of(spec.parentDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(parent)) {
            throw new BizException("目标父目录不存在：" + parent);
        }
        Path target = parent.resolve(spec.moduleDirName());
        if (Files.exists(target) && !isEmptyDirectory(target)) {
            throw new BizException("目标目录已存在且非空：" + target);
        }
        String spiVersion = spec.spiVersion() == null ? DEFAULT_SPI_VERSION : spec.spiVersion();
        Map<String, String> tokens = Map.of(
                "CODE", spec.code(),
                "DISPLAY_NAME", escapeQuoted(spec.displayName()),
                "DESCRIPTION", escapeQuoted(spec.description()),
                "VERSION", spec.version(),
                "SPI_VERSION", spiVersion,
                "PACKAGE", spec.basePackage(),
                "ENTRY_CLASS", spec.entryClassName(),
                "MAIN_CLASS", spec.mainClass(),
                "DEPEND_YAML", dependYaml("depend", spec.depend()),
                "SOFTDEPEND_YAML", dependYaml("softdepend", spec.softdepend()));
        List<String> files = new ArrayList<>();
        try {
            writeTemplate(target.resolve("pom.xml"), "pom.xml.tpl", tokens, files, target);
            writeTemplate(target.resolve("README.md"), "README.md.tpl", tokens, files, target);
            writeTemplate(target.resolve("src/main/resources/plugin.yml"), "plugin.yml.tpl", tokens, files, target);
            Path packageRoot = target.resolve("src/main/java").resolve(spec.basePackage().replace('.', '/'));
            writeTemplate(packageRoot.resolve("bootstrap").resolve(spec.entryClassName() + ".java"),
                    "Plugin.java.tpl", tokens, files, target);
            for (String dir : PACKAGE_DIRS) {
                Files.createDirectories(packageRoot.resolve(dir));
            }
        } catch (IOException e) {
            throw new BizException("插件骨架写入失败：" + e.getMessage());
        }
        log.info("插件脚手架已生成：{} -> {}", spec.code(), target);
        return new PluginScaffoldResult(spec.code(), target.toString(), spec.mainClass(), spiVersion, List.copyOf(files));
    }

    private void writeTemplate(Path target, String template, Map<String, String> tokens,
                               List<String> files, Path moduleRoot) throws IOException {
        String content = render(template, tokens);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        files.add(moduleRoot.relativize(target).toString().replace('\\', '/'));
    }

    private String render(String template, Map<String, String> tokens) {
        String content = readTemplate(template);
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            content = content.replace("{{" + token.getKey() + "}}", token.getValue());
        }
        return content;
    }

    private String readTemplate(String template) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(TEMPLATE_DIR + template)) {
            if (in == null) {
                throw new BizException("脚手架模板缺失：" + template);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("脚手架模板读取失败：" + template);
        }
    }

    /** depend/softdepend 的 YAML 片段；为空时整块省略。 */
    private String dependYaml(String key, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        StringBuilder yaml = new StringBuilder(key).append(":\n");
        for (String code : codes) {
            yaml.append("  - ").append(code).append('\n');
        }
        return yaml.toString();
    }

    /** Java 注解与 YAML 双引号标量共用的转义：反斜杠/双引号转义，换行收敛为空格。 */
    private String escapeQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\r', ' ').replace('\n', ' ');
    }

    private boolean isEmptyDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
