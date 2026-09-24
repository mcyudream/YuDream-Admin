package online.yudream.base.infra.system.about;

import online.yudream.base.domain.system.about.service.SpiBuildInfoGateway;
import online.yudream.base.domain.system.about.valobj.SpiBuildInfo;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * 从 SPI 构件自身携带的 spi-build.properties 读取版本：
 * 开发与打包形态都以 SPI 类的 ClassLoader 为准，读不到（例如老版本 SPI）返回空。
 */
@Component
public class ClasspathSpiBuildInfoGateway implements SpiBuildInfoGateway {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSpiBuildInfoGateway.class);
    private static final String RESOURCE = "spi-build.properties";

    private volatile Optional<SpiBuildInfo> cached;

    @Override
    public Optional<SpiBuildInfo> read() {
        Optional<SpiBuildInfo> hit = cached;
        if (hit == null) {
            hit = load();
            cached = hit;
        }
        return hit;
    }

    private Optional<SpiBuildInfo> load() {
        ClassLoader loader = YuDreamPlugin.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                log.warn("SPI 构件缺少 {}，关于页将显示 SPI 版本未知", RESOURCE);
                return Optional.empty();
            }
            Properties properties = new Properties();
            properties.load(input);
            String version = trimToNull(properties.getProperty("spi.version"));
            if (version == null) {
                return Optional.empty();
            }
            return Optional.of(new SpiBuildInfo(version,
                    trimToNull(properties.getProperty("spi.artifact")),
                    trimToNull(properties.getProperty("spi.build.time"))));
        } catch (IOException e) {
            log.warn("读取 SPI 构建信息失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // 资源过滤未生效时原样保留 ${...} 占位符，视为缺失
        return trimmed.isEmpty() || trimmed.contains("${") ? null : trimmed;
    }
}
