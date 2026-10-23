package online.yudream.base.infra.system.integration;

import com.sun.net.httpserver.HttpServer;
import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import online.yudream.base.infra.platform.plugin.service.ObjectStoragePluginFileStore;
import online.yudream.base.infra.system.file.service.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class StorageClientProviderTest {

    @Test
    void appliesBoundedApiTimeouts() {
        try (S3Client client = StorageClientProvider.build(config("http://127.0.0.1:1"))) {
            var overrides = client.serviceClientConfiguration().overrideConfiguration();
            assertEquals(Duration.ofSeconds(60), overrides.apiCallAttemptTimeout().orElseThrow());
            assertEquals(Duration.ofSeconds(120), overrides.apiCallTimeout().orElseThrow());
        }
    }

    @Test
    void reusesConnectionsAcrossMoreReadsThanPoolCapacity() throws Exception {
        byte[] image = "texture-content".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, image.length);
            try (var output = exchange.getResponseBody()) {
                output.write(image);
            } finally {
                exchange.close();
            }
        });
        server.start();
        StorageClientProvider provider = provider(config("http://127.0.0.1:" + server.getAddress().getPort()));
        try {
            var files = new ObjectStoragePluginFileStore("yudream-skin", new S3ObjectStorage(provider));
            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                for (int i = 0; i < 125; i++) {
                    var file = files.get("textures/hash");
                    assertEquals((long) image.length, file.contentLength());
                    try (var input = file.inputStream()) {
                        assertArrayEquals(image, input.readAllBytes());
                    }
                }
            });
            assertEquals(125, requests.get());
        } finally {
            provider.close();
            server.stop(0);
        }
    }

    @Test
    void cachesClientAndClearsItOnShutdown() {
        StorageClientProvider provider = provider(config("http://127.0.0.1:1"));
        try {
            S3Client first = provider.client();
            assertSame(first, provider.client());
            provider.close();
            provider.close();
            assertNotSame(first, provider.client());
        } finally {
            provider.close();
        }
    }

    private StorageClientProvider provider(ObjectStorageConfig config) {
        return new StorageClientProvider(new SystemIntegrationSettings(null, null, null) {
            @Override
            public ObjectStorageConfig effectiveStorage() {
                return config;
            }
        });
    }

    private ObjectStorageConfig config(String endpoint) {
        return new ObjectStorageConfig(endpoint, "test-access", "test-secret", "test-bucket", "us-east-1", true);
    }
}
