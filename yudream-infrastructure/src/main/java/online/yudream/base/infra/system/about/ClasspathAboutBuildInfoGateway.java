package online.yudream.base.infra.system.about;

import online.yudream.base.domain.system.about.service.AboutBuildInfoGateway;
import online.yudream.base.domain.system.about.valobj.AboutBuildInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * 从 classpath 的 about.properties 读取框架版本：文件由 Maven 资源过滤生成。
 * 不经 Environment 读取——未走 Maven 构建（IDE 直接编译 resources）时文件里是未解析的
 * ${project.version} 占位符，直接当缺失处理，避免占位符穿透到展示层。
 */
@Component
public class ClasspathAboutBuildInfoGateway implements AboutBuildInfoGateway {

    private static final Logger log = LoggerFactory.getLogger(ClasspathAboutBuildInfoGateway.class);
    private static final String RESOURCE = "about.properties";

    private volatile Optional<AboutBuildInfo> cached;

    @Override
    public Optional<AboutBuildInfo> read() {
        Optional<AboutBuildInfo> hit = cached;
        if (hit == null) {
            hit = load();
            cached = hit;
        }
        return hit;
    }

    private Optional<AboutBuildInfo> load() {
        try (InputStream input = ClasspathAboutBuildInfoGateway.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                log.warn("classpath 缺少 {}，关于页将显示框架版本未知", RESOURCE);
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(input);
            String version = trimToNull(properties.getProperty("about.framework.version"));
            if (version == null) {
                return Optional.empty();
            }
            return Optional.of(new AboutBuildInfo(version, trimToNull(properties.getProperty("about.framework.build.time"))));
        } catch (IOException e) {
            log.warn("读取框架构建信息失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.contains("${") ? null : trimmed;
    }
}
