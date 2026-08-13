package online.yudream.base.bootstrap.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * 将 JDK JUL 日志（java.util.logging）桥接到 SLF4J/logback，使插件（独立类加载器中仍使用 JUL）
 * 输出的日志进入宿主统一日志体系，从而被系统日志页面采集与查看。
 */
@Configuration
public class LoggingBridgeConfigure {

    @PostConstruct
    void installJulToSlf4jBridge() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LogManager.getLogManager().getLogger("").setLevel(Level.ALL);
    }
}
