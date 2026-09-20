package online.yudream.base.bootstrap.installer;

import online.yudream.base.domain.installer.BootstrapConfigFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapPropertiesEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    private StandardEnvironment environmentWith(Map<String, Object> props) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testSystem", props));
        return environment;
    }

    private Path writeBootstrapFile(String mongoUri) throws IOException {
        Path file = tempDir.resolve("yudream-bootstrap.properties");
        Properties props = new Properties();
        props.setProperty("spring.data.mongodb.uri", mongoUri);
        props.setProperty("yudream.credential.key", "abc");
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            props.store(writer, null);
        }
        return file;
    }

    @Test
    void fileSourceWinsOverEnvironmentAndMarksNormalMode() throws Exception {
        Path file = writeBootstrapFile("mongodb://from-file/yudream");
        StandardEnvironment environment = environmentWith(Map.of(
                BootstrapConfigFile.LOCATION_OVERRIDE_KEY, file.toString(),
                "MONGO_URI", "mongodb://from-env/other"));
        new BootstrapPropertiesEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("mongodb://from-file/yudream", environment.getProperty("spring.data.mongodb.uri"));
        assertEquals("abc", environment.getProperty("yudream.credential.key"));
        assertEquals("false", environment.getProperty(BootstrapConfigFile.PROPERTY_INSTALLER_MODE));
        assertEquals(file.toAbsolutePath().toString(),
                environment.getProperty(BootstrapConfigFile.PROPERTY_LOCATION));
        // 属性源必须位于最前，压过环境变量
        assertEquals(BootstrapPropertiesEnvironmentPostProcessor.PROPERTY_SOURCE_NAME,
                environment.getPropertySources().iterator().next().getName());
    }

    @Test
    void missingFileMarksInstallerModeWhenNoDatabaseConfig() {
        Path missing = tempDir.resolve("missing.properties");
        StandardEnvironment environment = environmentWith(Map.of(
                BootstrapConfigFile.LOCATION_OVERRIDE_KEY, missing.toString()));
        new BootstrapPropertiesEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("true", environment.getProperty(BootstrapConfigFile.PROPERTY_INSTALLER_MODE));
        assertEquals(missing.toAbsolutePath().toString(),
                environment.getProperty(BootstrapConfigFile.PROPERTY_LOCATION));
    }

    @Test
    void missingFileStillNormalModeWhenMongoEnvPresent() {
        Path missing = tempDir.resolve("missing.properties");
        StandardEnvironment environment = environmentWith(Map.of(
                BootstrapConfigFile.LOCATION_OVERRIDE_KEY, missing.toString(),
                "MONGO_URI", "mongodb://localhost/yudream"));
        new BootstrapPropertiesEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("false", environment.getProperty(BootstrapConfigFile.PROPERTY_INSTALLER_MODE));
        assertTrue(environment.getPropertySources().size() > 0);
    }
}
