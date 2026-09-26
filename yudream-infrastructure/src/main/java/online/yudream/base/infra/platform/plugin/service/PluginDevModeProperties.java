package online.yudream.base.infra.platform.plugin.service;

import lombok.Data;
import online.yudream.base.infra.platform.plugin.devmode.DevModeEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件开发模式配置：从插件源码仓库的编译产物目录直接加载并监听热重载，
 * 仅限本地开发使用，生产环境必须保持 enabled=false（或保持缺省并确保以 jar 运行）。
 * enabled 缺省时按宿主运行形态自动判定：源码运行（IDE / spring-boot:run）自动开启，
 * jar 运行自动关闭；显式配置 true/false 时以配置为准。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.plugin.dev-mode")
public class PluginDevModeProperties {

    /** null = 未配置，按宿主运行形态自动判定 */
    private Boolean enabled;
    /** 配置文件静态登记的开发项目，面板只读；面板维护的项目落在 storeFile */
    private List<DevProject> projects = new ArrayList<>();
    /** 面板维护的开发项目清单文件，缺省为插件目录下的 plugins/dev-projects.json（相对 user.dir） */
    private String storeFile;
    private long pollIntervalMs = 1_000;
    private long debounceMs = 800;
    private long compileTimeoutSeconds = 180;
    /**
     * 热编译附加 Maven 参数，属于部署者配置（application.yml/环境变量），而非面板操作者输入：
     * 宿主进程的默认 Maven 环境可能缺少插件源码仓所需的私服镜像与专用本地仓库，
     * 通过该参数追加 -s <settings>、-Dmaven.repo.local=<dir> 等，保证热编译与仓库自身构建一致。
     */
    private String compileArgs = "";

    public boolean effectiveEnabled(DevModeEnvironment environment) {
        return enabled != null ? enabled : environment.runningFromSource();
    }

    /** true 表示当前生效值来自自动检测而非显式配置 */
    public boolean autoDetected() {
        return enabled == null;
    }

    public Path resolvedStoreFile() {
        if (StringUtils.hasText(storeFile)) {
            return Path.of(storeFile).toAbsolutePath().normalize();
        }
        return Path.of("plugins").toAbsolutePath().normalize().resolve("dev-projects.json");
    }

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
        /**
         * 开发模式热编译固定命令（服务端固定值，不执行面板登记的操作者命令，防命令注入）。
         * 必须到达 process-classes 阶段：dev-export 的 copy-dependencies 绑定在该阶段，
         * 负责 runtime 依赖导出到 target/plugin-dev/lib，避免第三方 SDK 只在编译期可见。
         * 该字段不再被执行，仅为 dev-projects.json 兼容保留反序列化字段。
         */
        private String compileCommand = "mvn -q process-classes -DskipTests -P dev-export";

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
