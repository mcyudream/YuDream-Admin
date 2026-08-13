package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkPluginStoreGatewayTest {

    private static final URI ROOT = URI.create("https://store.example.test/repository/plugin-store-releases/index.json");
    private static final String SHA_256 = "A".repeat(64);

    @Test
    void acceptsOnlyHttpsRootsAndResolvedUris() {
        URI httpRoot = URI.create("http://store.example.test/repository/plugin-store-releases/index.json");
        assertTrue(JdkPluginStoreGateway.isValidRootUri(ROOT));
        assertFalse(JdkPluginStoreGateway.isValidRootUri(httpRoot));
        assertFalse(JdkPluginStoreGateway.isValidRootUri(URI.create("https://user@store.example.test/repository/plugin-store-releases/index.json")));
        assertFalse(JdkPluginStoreGateway.isValidRootUri(URI.create("https://store.example.test/repository/plugin-store-releases/index.json?x=1")));

        assertEquals(URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json"),
                JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, ROOT, "plugins/demo/index.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(httpRoot, httpRoot, "plugins/demo/index.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, httpRoot, "plugins/demo/index.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, ROOT, "../escape.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, ROOT, "https://evil.example/index.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, ROOT, "//evil.example/index.json"));
        assertNull(JdkPluginStoreGateway.resolveRelativeStoreUri(ROOT, ROOT, "demo/index.json?x=1"));
    }

    @Test
    void rejectsHttpRootConfiguredAtRuntime() {
        assertThrows(BizException.class, () -> gateway(
                URI.create("http://store.example.test/repository/plugin-store-releases/index.json"), new FakeHttpClient(request -> {
                    throw new AssertionError("HTTP root must be rejected before requesting it");
                })).list());
    }

    @Test
    void listSkipsInvalidRootsIndexesAndDescriptors() {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[
                      {"code":"good","index":"plugins/good/index.json"},
                      {"code":"bad-uri","index":"https://evil.example/index.json"},
                      {"code":"bad-index","index":"plugins/bad-index/index.json"},
                      {"code":"empty-versions","index":"plugins/empty-versions/index.json"},
                      {"code":"bad-descriptor","index":"plugins/bad-descriptor/index.json"},
                      {"code":"bad-code!","index":"bad/index.json"},
                      {"code":"missing-index"}
                    ]}
                    """;
            case "/repository/plugin-store-releases/plugins/good/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"good","versions":[
                      {"releaseVersion":"1.0.0","descriptor":"plugins/good/versions/1.0.0.json"},
                      {"releaseVersion":"2.0.0","descriptor":"plugins/good/versions/2.0.0.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/good/versions/2.0.0.json" -> descriptor("good", "2.0.0", "good.jar", SHA_256, null, null);
            case "/repository/plugin-store-releases/plugins/bad-index/index.json" -> "{\"schemaVersion\":2}";
            case "/repository/plugin-store-releases/plugins/empty-versions/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"empty-versions","versions":[]}
                    """;
            case "/repository/plugin-store-releases/plugins/bad-descriptor/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"bad-descriptor","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/bad-descriptor/versions/1.0.0.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/bad-descriptor/versions/1.0.0.json" -> descriptor("bad-descriptor", "1.0.0", "bad.jar", "not-a-hash", null, null);
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        List<PluginStorePluginInfo> plugins = gateway(ROOT, client).list();

        assertEquals(List.of("good"), plugins.stream().map(PluginStorePluginInfo::getCode).toList());
        assertEquals("2.0.0", plugins.getFirst().getDescriptor().releaseVersion());
        assertEquals("good", plugins.getFirst().getDescriptor().code());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/good/good.jar",
                plugins.getFirst().getDescriptor().jar().url());
        assertEquals(List.of(ROOT,
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/good/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/good/versions/2.0.0.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-index/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/empty-versions/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-descriptor/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-descriptor/versions/1.0.0.json")), client.requests);
    }

    @Test
    void detailResolvesJarAndMediaFromPluginIndexWithoutFetchingJar() {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor("demo", "1.0.0", "jars/demo.jar", SHA_256,
                    "images/icon.svg", "[\"images/one.png\",\"images/two.png\"]");
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        PluginStorePluginDetail detail = gateway(ROOT, client).detail("demo").orElseThrow();

        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/demo/jars/demo.jar", detail.versions().getFirst().descriptor().jar().url());
        assertEquals(SHA_256.toLowerCase(), detail.versions().getFirst().descriptor().jar().sha256());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/demo/images/icon.svg", detail.versions().getFirst().descriptor().icon());
        assertEquals(List.of("https://store.example.test/repository/plugin-store-releases/plugins/demo/images/one.png",
                "https://store.example.test/repository/plugin-store-releases/plugins/demo/images/two.png"), detail.versions().getFirst().descriptor().screenshots());
        assertEquals(List.of(ROOT, URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json")), client.requests);
    }

    @Test
    void detailKeepsAbsoluteHttpsJarUrlWithoutFetchingJar() {
        String jarUrl = "https://nexus.yudream.online/repository/maven-public/demo.jar";
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor("demo", "1.0.0", jarUrl, SHA_256, null, null);
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        PluginStorePluginDetail detail = gateway(ROOT, client).detail("demo").orElseThrow();

        assertEquals(jarUrl, detail.versions().getFirst().descriptor().jar().url());
        assertEquals(List.of(ROOT, URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json")), client.requests);
    }

    @Test
    void detailReturnsEveryVersionInIndexOrder() {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> """
                    {"schemaVersion":1,"pluginCode":"demo","versions":[
                      {"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"},
                      {"releaseVersion":"2.0.0","descriptor":"plugins/demo/versions/2.0.0.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor("demo", "1.0.0", "one.jar", SHA_256, null, null);
            case "/repository/plugin-store-releases/plugins/demo/versions/2.0.0.json" -> descriptor("demo", "2.0.0", "two.jar", SHA_256, null, null);
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        PluginStorePluginDetail detail = gateway(ROOT, client).detail("demo").orElseThrow();

        assertEquals(List.of("1.0.0", "2.0.0"), detail.versions().stream()
                .map(version -> version.releaseVersion()).toList());
        assertEquals(List.of("1.0.0", "2.0.0"), detail.versions().stream()
                .map(version -> version.descriptor().version()).toList());
    }

    @Test
    void detailRejectsEmptyOrDuplicateVersionsAndInvalidHash() {
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[]}
                """, null);
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[
                  {"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"},
                  {"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/2.0.0.json"}]}
                """, null);
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, descriptor("demo", "1.0.0", "jar.jar", "not-a-hash", null, null));
    }

    @Test
    void detailRejectsSchemaCodeVersionAndMediaReferenceViolations() {
        assertDetailRejected("""
                {"schemaVersion":2,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, null);
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"other","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, null);
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, descriptor("demo", "2.0.0", "jar.jar", SHA_256, null, null));
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, descriptor("demo", "1.0.0", "jar.jar", SHA_256, "https://evil.example/icon.svg", null));
    }

    @Test
    void detailParsesCompatibilityAndDependencies() {
        String descriptor = "{\"schemaVersion\":1,\"releaseVersion\":\"1.0.0\",\"plugin\":{\"code\":\"demo\",\"version\":\"1.0.0\",\"main\":\"example.Plugin\","
                + "\"compatibility\":{\"host\":\">=1.0.0 <2.0.0\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"~1.0.0\"},"
                + "\"dependencies\":[{\"code\":\"base\",\"range\":\"1.2.x\",\"required\":true},{\"code\":\"optional\",\"range\":\"^9.0.0\",\"required\":false}]},"
                + "\"jar\":{\"mavenCoordinates\":\"g:a:1.0.0\",\"url\":\"jar.jar\",\"sha256\":\"" + SHA_256 + "\"}}";
        PluginStorePluginDescriptor result = detailForDescriptor(descriptor);

        assertEquals(">=1.0.0 <2.0.0", result.compatibility().host());
        assertEquals("^2.6.0", result.compatibility().spi());
        assertEquals("~1.0.0", result.compatibility().frontendSdk());
        assertEquals(List.of("base", "optional"), result.dependencies().stream().map(dependency -> dependency.code()).toList());
        assertTrue(result.dependencies().getFirst().required());
        assertFalse(result.dependencies().get(1).required());
    }

    @Test
    void detailParsesCompatibilityFieldSubsets() {
        PluginStorePluginDescriptor hostOnly = detailForDescriptor(descriptorWithPluginFields("\"compatibility\":{\"host\":\"^1.0.0\"}"));
        assertEquals("^1.0.0", hostOnly.compatibility().host());
        assertNull(hostOnly.compatibility().spi());
        assertNull(hostOnly.compatibility().frontendSdk());

        PluginStorePluginDescriptor empty = detailForDescriptor(descriptorWithPluginFields("\"compatibility\":{}"));
        assertNull(empty.compatibility().host());
        assertNull(empty.compatibility().spi());
        assertNull(empty.compatibility().frontendSdk());
    }

    @Test
    void detailRejectsInvalidSemanticVersionsCompatibilityAndDependencies() {
        assertDetailRejected("""
                {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0-beta","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """, null);
        for (String invalidCompatibility : new String[]{"^1.0.0 || ^2.0.0", "[1.0.0,2.0.0)", "1.0.0-beta", ">=2.0.0 <1.0.0"}) {
            assertDetailRejected(validIndex(), descriptorWithPluginFields("\"compatibility\":{\"host\":\"" + invalidCompatibility
                    + "\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"^1.0.0\"}"));
        }
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"compatibility\":{\"host\":\"\"}"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"compatibility\":{\"host\":true}"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"compatibility\":{\"host\":\"^1.0.0\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"^1.0.0\",\"extra\":true}"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":\"true\"}]"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":true,\"extra\":true}]"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\"}]"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\">=2.0.0 <1.0.0\",\"required\":true}]"));
        assertDetailRejected(validIndex(), descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":true},{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":false}]"));
    }

    @Test
    void detailParsesOptionalDisplayMetadataWithoutAffectingJarContract() {
        PluginStorePluginDescriptor result = detailForDescriptor("""
                {"schemaVersion":1,"releaseVersion":"1.0.0","plugin":{"code":"demo","version":"1.0.0","main":"example.Plugin",
                "publisher":{"id":"yudream","name":"YuDream","url":"https://yudream.online","verified":true},
                "source":{"repository":"https://github.com/yudream/demo","commit":"0123456789abcdef0123456789abcdef01234567"},
                "license":"Apache-2.0","releaseNotes":"Bug fixes and stability improvements"},
                "jar":{"mavenCoordinates":"g:a:1.0.0","url":"jar.jar","sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}}
                """);

        assertEquals("yudream", result.publisher().id());
        assertTrue(result.publisher().verified());
        assertEquals("https://github.com/yudream/demo", result.source().repository());
        assertEquals("0123456789abcdef0123456789abcdef01234567", result.source().commit());
        assertEquals("Apache-2.0", result.license());
        assertEquals("Bug fixes and stability improvements", result.releaseNotes());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/demo/jar.jar", result.jar().url());
    }

    @Test
    void detailKeepsLegacyDescriptorDisplayMetadataAbsent() {
        PluginStorePluginDescriptor result = detailForDescriptor(descriptor("demo", "1.0.0", "jar.jar", SHA_256, null, null));

        assertNull(result.publisher());
        assertNull(result.source());
        assertNull(result.license());
        assertNull(result.releaseNotes());
    }

    @Test
    void detailRejectsUnsafeOrMalformedDisplayMetadata() {
        String valid = "\"publisher\":{\"id\":\"publisher\",\"name\":\"Publisher\",\"url\":\"https://publisher.example\",\"verified\":false},"
                + "\"source\":{\"repository\":\"https://github.com/example/demo\",\"commit\":\"0123456789abcdef0123456789abcdef01234567\"},"
                + "\"license\":\"MIT\",\"releaseNotes\":\"Notes\"";
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("https://publisher.example", "http://publisher.example")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("https://publisher.example", "https://user@publisher.example")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("https://github.com/example/demo", "https://github.com/example/demo#fragment")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("0123456789abcdef0123456789abcdef01234567", "0123456789abcdef0123456789abcdef0123456G")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("\"verified\":false", "\"verified\":\"false\"")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("\"license\":\"MIT\"", "\"license\":\"MIT OR Apache-2.0\"")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("\"releaseNotes\":\"Notes\"", "\"releaseNotes\":\"line\nfeed\"")));
        assertDetailRejected(validIndex(), descriptorWithPluginFields(valid.replace("\"verified\":false", "\"verified\":false,\"artifactUrl\":\"https://evil.example/plugin.jar\"")));
    }

    @Test
    void downloadsJarToProvidedTargetAfterValidatingHashAndSize() throws IOException {
        byte[] jar = "plugin-jar".getBytes(StandardCharsets.UTF_8);
        Path target = Files.createTempFile("plugin-store-", ".jar");
        Files.deleteIfExists(target);
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 200, "application/java-archive", jar));

        gateway(ROOT, client).downloadJar(storeDescriptor("https://store.example.test/repository/maven/demo.jar", sha256(jar)), target);

        assertEquals("plugin-jar", Files.readString(target));
        assertEquals(List.of(URI.create("https://store.example.test/repository/maven/demo.jar")), client.requests);
        Files.deleteIfExists(target);
    }

    @Test
    void rejectsUnsafeOrInvalidJarDownloadsWithoutLeavingTarget() throws IOException {
        Path target = Files.createTempFile("plugin-store-", ".jar");
        Files.deleteIfExists(target);
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 302, "application/java-archive", new byte[0]));

        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(
                storeDescriptor("http://store.example.test/demo.jar", SHA_256), target));
        assertFalse(Files.exists(target));
        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(
                storeDescriptor("https://store.example.test/demo.jar", SHA_256), target));
        assertFalse(Files.exists(target));
    }

    @Test
    void rejectsOversizedAndHashMismatchedJarDownloads() throws IOException {
        byte[] jar = "plugin-jar".getBytes(StandardCharsets.UTF_8);
        Path target = Files.createTempFile("plugin-store-", ".jar");
        Files.deleteIfExists(target);
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 200, "application/java-archive", jar));
        PluginProperties properties = new PluginProperties();
        properties.setStoreRootUrl(ROOT.toString());
        properties.setStoreMaxJarBytes(jar.length - 1);
        JdkPluginStoreGateway gateway = new JdkPluginStoreGateway(properties, client, new ObjectMapper());

        assertThrows(BizException.class, () -> gateway.downloadJar(
                storeDescriptor("https://store.example.test/demo.jar", sha256(jar)), target));
        assertFalse(Files.exists(target));
        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(
                storeDescriptor("https://store.example.test/demo.jar", SHA_256), target));
        assertFalse(Files.exists(target));
    }

    private void assertDetailRejected(String index, String descriptor) {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> index;
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> {
                if (descriptor == null) {
                    throw new AssertionError("Descriptor should not be requested");
                }
                yield descriptor;
            }
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        assertThrows(BizException.class, () -> gateway(ROOT, client).detail("demo"));
    }

    private PluginStorePluginDescriptor detailForDescriptor(String descriptor) {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> validIndex();
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor;
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));
        return gateway(ROOT, client).detail("demo").orElseThrow().versions().getFirst().descriptor();
    }

    private static String validIndex() {
        return """
                {"schemaVersion":1,"pluginCode":"demo","versions":[{"releaseVersion":"1.0.0","descriptor":"plugins/demo/versions/1.0.0.json"}]}
                """;
    }

    private static String descriptorWithPluginFields(String fields) {
        return "{\"schemaVersion\":1,\"releaseVersion\":\"1.0.0\",\"plugin\":{\"code\":\"demo\",\"version\":\"1.0.0\",\"main\":\"example.Plugin\"," + fields
                + "},\"jar\":{\"mavenCoordinates\":\"g:a:1.0.0\",\"url\":\"jar.jar\",\"sha256\":\"" + SHA_256 + "\"}}";
    }

    private JdkPluginStoreGateway gateway(URI root, HttpClient client) {
        PluginProperties properties = new PluginProperties();
        properties.setStoreRootUrl(root.toString());
        properties.setStoreConnectTimeoutMillis(2_000);
        properties.setStoreRequestTimeoutMillis(2_000);
        properties.setStoreMaxResponseBytes(16_384);
        return new JdkPluginStoreGateway(properties, client, new ObjectMapper());
    }

    private static PluginStorePluginDescriptor storeDescriptor(String url, String sha256) {
        return new PluginStorePluginDescriptor("1.0.0", "demo", "1.0.0", "example.Plugin", null, null, null,
                List.of(), null, List.of(), new PluginStorePluginJar("example:demo:1.0.0", url, sha256));
    }

    private static String descriptor(String code, String version, String jarUrl, String sha256, String icon, String screenshots) {
        String media = icon == null ? "" : ",\"icon\":\"" + icon + "\"";
        media += screenshots == null ? "" : ",\"screenshots\":" + screenshots;
        return "{\"schemaVersion\":1,\"releaseVersion\":\"" + version + "\",\"plugin\":{\"code\":\"" + code
                + "\",\"version\":\"" + version + "\",\"main\":\"example.Plugin\"" + media
                + "},\"jar\":{\"mavenCoordinates\":\"g:a:" + version + "\",\"url\":\"" + jarUrl
                + "\",\"sha256\":\"" + sha256 + "\"}}";
    }

    private static HttpResponse<java.io.InputStream> response(HttpRequest request, String body) {
        return response(request, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpResponse<java.io.InputStream> response(HttpRequest request, int status, String contentType, byte[] body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return request; }
            @Override public Optional<HttpResponse<java.io.InputStream>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (a, b) -> true); }
            @Override public java.io.InputStream body() { return new ByteArrayInputStream(body); }
            @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return request.uri(); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static class FakeHttpClient extends HttpClient {
        private final List<URI> requests = new ArrayList<>();
        private final Function<HttpRequest, HttpResponse<java.io.InputStream>> responder;

        private FakeHttpClient(Function<HttpRequest, HttpResponse<java.io.InputStream>> responder) {
            this.responder = responder;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { throw new UnsupportedOperationException(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
            requests.add(request.uri());
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responder.apply(request);
            return response;
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
    }
}
