package online.yudream.base.domain.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapConfigFileTest {

    @TempDir
    Path tempDir;

    @Test
    void locateUsesOverrideWhenPresent() throws Exception {
        Path override = Files.createFile(tempDir.resolve("custom.properties"));
        Function<String, String> lookup = key ->
                BootstrapConfigFile.LOCATION_OVERRIDE_KEY.equals(key) ? override.toString() : null;
        assertEquals(override, BootstrapConfigFile.locate(lookup));
    }

    @Test
    void locateFallsBackToUserDirRelativePath() {
        Function<String, String> lookup = key -> null;
        Path expected = Path.of(System.getProperty("user.dir"),
                BootstrapConfigFile.DEFAULT_RELATIVE_PATH);
        assertEquals(expected, BootstrapConfigFile.locate(lookup));
    }

    @Test
    void placeholderOnlyUriDoesNotCountAsConfigured() {
        Function<String, String> applicationYmlStyle = key -> {
            if (BootstrapConfigFile.MONGO_URI_PROPERTY.equals(key)) {
                return "${MONGO_URI}";
            }
            return null;
        };
        assertFalse(BootstrapConfigFile.hasDatabaseConfiguration(applicationYmlStyle));
    }

    @Test
    void explicitUriCountsAsConfigured() {
        Function<String, String> explicit = key ->
                BootstrapConfigFile.MONGO_URI_PROPERTY.equals(key) ? "mongodb://mongo:27017/db" : null;
        assertTrue(BootstrapConfigFile.hasDatabaseConfiguration(explicit));
    }

    @Test
    void mongoEnvVarCountsAsConfigured() {
        Function<String, String> envOnly = key ->
                BootstrapConfigFile.MONGO_URI_ENV_KEY.equals(key) ? "mongodb://localhost/yudream" : null;
        assertTrue(BootstrapConfigFile.hasDatabaseConfiguration(envOnly));
    }

    @Test
    void nothingConfiguredMeansInstallerMode() {
        Function<String, String> nothing = key -> null;
        assertFalse(BootstrapConfigFile.hasDatabaseConfiguration(nothing));
    }

    @Test
    void installerModeTrueWhenNothingConfigured() {
        Path missing = tempDir.resolve("missing.properties");
        Function<String, String> nothing = key -> null;
        assertTrue(BootstrapConfigFile.installerMode(new String[0], missing, nothing));
    }

    @Test
    void installerModeFalseWhenFileExists() throws Exception {
        Path existing = Files.createFile(tempDir.resolve("bootstrap.properties"));
        Function<String, String> nothing = key -> null;
        assertFalse(BootstrapConfigFile.installerMode(new String[0], existing, nothing));
    }

    @Test
    void installerModeFalseWhenArgsProvideUri() {
        Path missing = tempDir.resolve("missing.properties");
        Function<String, String> nothing = key -> null;
        String[] args = {"--spring.data.mongodb.uri=mongodb://localhost/yudream"};
        assertFalse(BootstrapConfigFile.installerMode(args, missing, nothing));
        String[] envStyleArgs = {"--MONGO_URI=mongodb://localhost/yudream"};
        assertFalse(BootstrapConfigFile.installerMode(envStyleArgs, missing, nothing));
    }
}
