package online.yudream.base.bootstrap.installer;

import online.yudream.base.domain.installer.BootstrapConfigFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * 引导配置装载器：把安装向导生成的 {@code config/yudream-bootstrap.properties}
 * 以最高优先级属性源注入（压过环境变量与 application.yml），并注入
 * {@code yudream.bootstrap.installer} 安装器模式标记与引导文件位置。
 */
public class BootstrapPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "yudreamBootstrapInstaller";
    /** MONGO_URI 的环境变量宽松绑定名（SPRING_DATA_MONGODB_URI → spring.data.mongodb.uri）。 */
    private static final String MONGO_URI_ENV_RELAXED = "SPRING_DATA_MONGODB_URI";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Function<String, String> rawLookup = rawLookup(environment);
        Path location = BootstrapConfigFile.locate(rawLookup);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(BootstrapConfigFile.PROPERTY_LOCATION, location.toAbsolutePath().toString());
        if (Files.exists(location)) {
            Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(location, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("读取引导配置文件失败：" + location, e);
            }
            props.forEach((key, value) -> source.put(String.valueOf(key), String.valueOf(value)));
            source.put(BootstrapConfigFile.PROPERTY_INSTALLER_MODE, "false");
        } else {
            boolean hasDatabase = BootstrapConfigFile.hasDatabaseConfiguration(rawLookup)
                    || StringUtils.hasText(rawLookup.apply(MONGO_URI_ENV_RELAXED));
            source.put(BootstrapConfigFile.PROPERTY_INSTALLER_MODE, Boolean.toString(!hasDatabase));
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, source));
    }

    /**
     * 按属性源直接取原始值：environment.getProperty 遇到 application.yml 中未解析的
     * {@code ${MONGO_URI}} 占位符会抛 PlaceholderResolutionException，安装器判定必须绕开它。
     */
    private Function<String, String> rawLookup(ConfigurableEnvironment environment) {
        return key -> {
            for (PropertySource<?> source : environment.getPropertySources()) {
                Object value = source.getProperty(key);
                if (value instanceof String text) {
                    return text;
                }
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return null;
        };
    }

    @Override
    public int getOrder() {
        // 紧随 ConfigData 之后执行，保证属性源位于 application.yml 与环境变量之上
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
