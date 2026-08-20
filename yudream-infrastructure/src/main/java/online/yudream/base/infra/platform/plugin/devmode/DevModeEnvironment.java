package online.yudream.base.infra.platform.plugin.devmode;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.plugin.service.JarPluginRuntimeGateway;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 宿主运行形态检测：区分源码运行（IDE / spring-boot:run，类来自 target/classes 目录）
 * 与 jar 运行（java -jar，类来自 BOOT-INF 嵌套 jar）。开发模式未显式配置 enabled 时，
 * 以源码运行作为自动开启依据；检测在启动时执行一次并缓存。
 */
@Slf4j
@Component
public class DevModeEnvironment {

    private final boolean runningFromSource = detectRunningFromSource();

    public boolean runningFromSource() {
        return runningFromSource;
    }

    public String hostRunMode() {
        return runningFromSource ? "SOURCE" : "JAR";
    }

    private static boolean detectRunningFromSource() {
        try {
            URL location = JarPluginRuntimeGateway.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null || !"file".equals(location.getProtocol())) {
                return false;
            }
            // 源码运行时代码源是 target/classes 目录；jar 运行时是 .jar 文件或嵌套 jar URL
            return Files.isDirectory(Path.of(location.toURI()));
        } catch (Exception e) {
            log.warn("检测宿主运行形态失败，按 jar 运行处理：{}", e.getMessage());
            return false;
        }
    }
}
