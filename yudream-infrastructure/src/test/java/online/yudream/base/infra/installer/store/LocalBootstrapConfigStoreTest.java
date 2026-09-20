package online.yudream.base.infra.installer.store;

import online.yudream.base.domain.installer.valobj.BootstrapConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalBootstrapConfigStoreTest {

    @TempDir
    Path tempDir;

    private Path target;

    private LocalBootstrapConfigStore store() throws IOException {
        target = tempDir.resolve("config/yudream-bootstrap.properties");
        return new LocalBootstrapConfigStore(target.toString());
    }

    @Test
    void saveWritesAllKeysAndRoundTrips() throws Exception {
        LocalBootstrapConfigStore store = store();
        BootstrapConfig config = new BootstrapConfig(
                "mongodb://admin:p@ss:word@mongo:27017/yudream?authSource=admin",
                "redis", 6380, "redis:pass=1", 2, false,
                "67M7G+5hekFUH3BRfkrLv0JTBVaspj8gQh16z3uyVeI=", 3, 4);
        store.save(config);

        assertTrue(Files.exists(target));
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        assertEquals(config.mongoUri(), props.getProperty("spring.data.mongodb.uri"));
        assertEquals("redis", props.getProperty("spring.data.redis.host"));
        assertEquals("6380", props.getProperty("spring.data.redis.port"));
        assertEquals("redis:pass=1", props.getProperty("spring.data.redis.password"));
        assertEquals("2", props.getProperty("spring.data.redis.database"));
        assertEquals(config.credentialKey(), props.getProperty("yudream.credential.key"));
        assertEquals("3", props.getProperty("snowflake.data-center-id"));
        assertEquals("4", props.getProperty("snowflake.machine-id"));
        assertTrue(props.containsKey("yudream.bootstrap.installed-at"));
        assertFalse(props.containsKey("spring.data.redis.ssl.enabled"));
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".tmp")));
    }

    @Test
    void saveWritesSslKeyWhenEnabled() throws Exception {
        LocalBootstrapConfigStore store = store();
        store.save(new BootstrapConfig("mongodb://mongo:27017/yudream",
                "redis", null, null, null, true, "k", null, null));
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        assertEquals("true", props.getProperty("spring.data.redis.ssl.enabled"));
        assertEquals("6379", props.getProperty("spring.data.redis.port"));
        assertFalse(props.containsKey("spring.data.redis.password"));
    }

    @Test
    void existsReflectsFilePresence() throws Exception {
        LocalBootstrapConfigStore store = store();
        assertFalse(store.exists());
        store.save(new BootstrapConfig("mongodb://mongo:27017/yudream", "redis",
                null, null, null, null, "k", null, null));
        assertTrue(store.exists());
    }

    @Test
    void saveRejectsIncompleteConfig() throws Exception {
        LocalBootstrapConfigStore store = store();
        assertThrows(IllegalArgumentException.class, () -> store.save(
                new BootstrapConfig("", "redis", null, null, null, null, "k", null, null)));
        assertFalse(store.exists());
    }
}
