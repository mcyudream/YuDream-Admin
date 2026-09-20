package online.yudream.base.domain.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * 引导配置文件定位与「是否已具备数据库配置」的判定。
 * 该判定被启动入口（Spring 环境就绪前）与 EnvironmentPostProcessor 共用，
 * 因此只依赖 JDK，不依赖任何框架类型。
 */
public final class BootstrapConfigFile {

    /** 覆盖引导配置文件位置的部署级变量（环境变量或系统属性）。 */
    public static final String LOCATION_OVERRIDE_KEY = "YUDREAM_BOOTSTRAP_CONFIG";
    /** 相对工作目录的默认引导配置位置。 */
    public static final String DEFAULT_RELATIVE_PATH = "config/yudream-bootstrap.properties";
    /** EnvironmentPostProcessor 注入的引导文件实际位置属性。 */
    public static final String PROPERTY_LOCATION = "yudream.bootstrap.config-file";
    /** EnvironmentPostProcessor 注入的安装器模式标记属性。 */
    public static final String PROPERTY_INSTALLER_MODE = "yudream.bootstrap.installer";
    /** 数据库连接的环境变量与属性名（与 application.yml 占位符约定一致）。 */
    public static final String MONGO_URI_ENV_KEY = "MONGO_URI";
    public static final String MONGO_URI_PROPERTY = "spring.data.mongodb.uri";

    private BootstrapConfigFile() {
    }

    /**
     * 按给定属性查找函数解析引导配置文件位置（不要求文件存在）。
     */
    public static Path locate(Function<String, String> propertyLookup) {
        String configured = propertyLookup.apply(LOCATION_OVERRIDE_KEY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        return defaultLocation();
    }

    /**
     * Spring 环境就绪前的默认定位：系统属性优先于环境变量。
     */
    public static Path locateDefault() {
        String prop = System.getProperty(LOCATION_OVERRIDE_KEY);
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop.trim());
        }
        String env = System.getenv(LOCATION_OVERRIDE_KEY);
        if (env != null && !env.isBlank()) {
            return Path.of(env.trim());
        }
        return defaultLocation();
    }

    /**
     * 是否已具备数据库配置：显式属性/环境变量任一存在即视为已配置。
     * application.yml 中的 {@code ${MONGO_URI}} 占位符不算配置。
     */
    public static boolean hasDatabaseConfiguration(Function<String, String> propertyLookup) {
        String uri = propertyLookup.apply(MONGO_URI_PROPERTY);
        if (uri != null && !uri.isBlank() && !uri.contains("${")) {
            return true;
        }
        String env = propertyLookup.apply(MONGO_URI_ENV_KEY);
        return env != null && !env.isBlank();
    }

    /**
     * Spring 环境就绪前的判定：系统属性 + 环境变量。
     */
    public static boolean hasDatabaseConfigurationFromSystem() {
        return hasDatabaseConfiguration(systemLookup());
    }

    /**
     * 系统属性 + 环境变量的属性查找函数。
     */
    public static Function<String, String> systemLookup() {
        return key -> {
            String value = System.getProperty(key);
            if (value != null) {
                return value;
            }
            if (MONGO_URI_PROPERTY.equals(key)) {
                return System.getenv(MONGO_URI_ENV_KEY);
            }
            return null;
        };
    }

    /**
     * 当前进程是否应以安装器模式启动：无引导配置文件且未预置数据库配置。
     */
    public static boolean installerModeFromSystem(String[] args) {
        return installerMode(args, locateDefault(), systemLookup());
    }

    /**
     * 判定核心（可注入便于测试）：给定引导文件位置与属性查找函数。
     */
    public static boolean installerMode(String[] args, Path configFileLocation,
                                        Function<String, String> propertyLookup) {
        if (Files.exists(configFileLocation)) {
            return false;
        }
        if (hasDatabaseConfiguration(propertyLookup)) {
            return false;
        }
        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--" + MONGO_URI_PROPERTY + "=") || arg.startsWith("--" + MONGO_URI_ENV_KEY + "=")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Path defaultLocation() {
        return Path.of(System.getProperty("user.dir"), DEFAULT_RELATIVE_PATH);
    }
}
