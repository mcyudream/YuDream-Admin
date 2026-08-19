package online.yudream.base.infra.platform.plugin.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件开发模式配置：从插件源码仓库的编译产物目录直接加载并监听热重载，
 * 仅限本地开发使用，生产环境必须保持 enabled=false。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.plugin.dev-mode")
public class PluginDevModeProperties {

    private boolean enabled = false;
    private List<DevProject> projects = new ArrayList<>();
    private long pollIntervalMs = 1_000;
    private long debounceMs = 800;
    private long compileTimeoutSeconds = 180;

    @Data
    public static class DevProject {
        /** 插件 code，必须与 plugin.yml 的 name 一致 */
        private String code;
        /** 插件模块根目录（含 pom.xml 的目录） */
        private String path;
        /** 前端产物目录，缺省按官方插件仓布局推导 */
        private String frontendDist;
        /** 监听到 src/main/java 变化时是否自动执行编译命令 */
        private boolean autoCompile = true;
        private String compileCommand = "mvn -q compile -DskipTests";

        public Path classesDir() {
            return Path.of(path).toAbsolutePath().normalize().resolve("target").resolve("classes");
        }

        public Path libDir() {
            return Path.of(path).toAbsolutePath().normalize().resolve("target").resolve("plugin-dev").resolve("lib");
        }

        public Path sourceDir() {
            return Path.of(path).toAbsolutePath().normalize().resolve("src").resolve("main").resolve("java");
        }

        public Path resolvedFrontendDist() {
            if (StringUtils.hasText(frontendDist)) {
                return Path.of(frontendDist).toAbsolutePath().normalize();
            }
            // 官方插件仓布局：yudream-plugins/yudream-plugin-{code} 与 yudream-frontend/packages/plugin-{code}
            return Path.of(path).toAbsolutePath().normalize()
                    .resolve("..").resolve("..").resolve("yudream-frontend")
                    .resolve("packages").resolve("plugin-" + code).resolve("dist")
                    .normalize();
        }
    }
}
