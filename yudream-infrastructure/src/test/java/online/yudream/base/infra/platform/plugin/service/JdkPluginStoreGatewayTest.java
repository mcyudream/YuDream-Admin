package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginJar;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;
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
    private static final String INDEX_URL = ROOT.toString();
    private static final String DEMO_INDEX_URL = "https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json";
    private static final String DEMO_DESCRIPTOR_URL = "https://store.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json";
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
    void configuredSourceRefMirrorsProperties() {
        assertEquals(ROOT.toString(), gateway(ROOT, new FakeHttpClient(request -> {
            throw new AssertionError("configuredSourceRef must not request anything");
        })).configuredSourceRef().rootUrl());
    }

    @Test
    void rejectsHttpRootConfiguredAtRuntime() {
        assertThrows(BizException.class, () -> gateway(
                URI.create("http://store.example.test/repository/plugin-store-releases/index.json"), new FakeHttpClient(request -> {
                    throw new AssertionError("HTTP root must be rejected before requesting it");
                })).fetchCatalog(new PluginStoreSourceRef(
                "http://store.example.test/repository/plugin-store-releases/index.json", null)));
    }

    @Test
    void fetchCatalogSkipsInvalidRootsIndexesAndDescriptors() {
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

        List<PluginStoreCatalogEntry> entries = gateway(ROOT, client).fetchCatalog(ref());

        assertEquals(List.of("good"), entries.stream().map(PluginStoreCatalogEntry::code).toList());
        PluginStoreCatalogEntry good = entries.getFirst();
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/good/index.json", good.indexUrl());
        assertEquals(List.of("1.0.0", "2.0.0"), good.versions().stream().map(version -> version.releaseVersion()).toList());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/good/versions/2.0.0.json",
                good.versions().getLast().descriptorUrl());
        PluginStorePluginDescriptor parsed = gateway(ROOT, client).parseDescriptor(ref(), good.indexUrl(), good.latestDescriptorJson());
        assertEquals("2.0.0", parsed.releaseVersion());
        assertEquals("good", parsed.code());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/good/good.jar", parsed.jar().url());
        assertEquals(List.of(ROOT,
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/good/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/good/versions/2.0.0.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-index/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/empty-versions/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-descriptor/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/bad-descriptor/versions/1.0.0.json")), client.requests);
    }

    @Test
    void fetchCatalogResolvesJarAndMediaFromPluginIndexWithoutFetchingJar() {
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

        PluginStoreCatalogEntry entry = gateway(ROOT, client).fetchCatalog(ref()).getFirst();
        PluginStorePluginDescriptor descriptor = gateway(ROOT, client).parseDescriptor(ref(), entry.indexUrl(), entry.latestDescriptorJson());

        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/demo/jars/demo.jar", descriptor.jar().url());
        assertEquals(SHA_256.toLowerCase(), descriptor.jar().sha256());
        assertEquals("https://store.example.test/repository/plugin-store-releases/plugins/demo/images/icon.svg", descriptor.icon());
        assertEquals(List.of("https://store.example.test/repository/plugin-store-releases/plugins/demo/images/one.png",
                "https://store.example.test/repository/plugin-store-releases/plugins/demo/images/two.png"), descriptor.screenshots());
        assertEquals(List.of(ROOT, URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json")), client.requests);
    }

    @Test
    void fetchCatalogKeepsAbsoluteHttpsJarUrlWithoutFetchingJar() {
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

        PluginStoreCatalogEntry entry = gateway(ROOT, client).fetchCatalog(ref()).getFirst();
        PluginStorePluginDescriptor descriptor = gateway(ROOT, client).parseDescriptor(ref(), entry.indexUrl(), entry.latestDescriptorJson());

        assertEquals(jarUrl, descriptor.jar().url());
        assertEquals(List.of(ROOT, URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/index.json"),
                URI.create("https://store.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json")), client.requests);
    }

    @Test
    void fetchDescriptorResolvesEveryVersionInIndexOrder() {
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

        JdkPluginStoreGateway gateway = gateway(ROOT, client);
        PluginStoreCatalogEntry entry = gateway.fetchCatalog(ref()).getFirst();
        assertEquals(List.of("1.0.0", "2.0.0"), entry.versions().stream().map(version -> version.releaseVersion()).toList());

        PluginStorePluginDescriptor first = gateway.fetchDescriptor(ref(), entry.indexUrl(), entry.versions().getFirst().descriptorUrl());
        PluginStorePluginDescriptor second = gateway.fetchDescriptor(ref(), entry.indexUrl(), entry.versions().getLast().descriptorUrl());
        assertEquals("1.0.0", first.version());
        assertEquals("2.0.0", second.version());
        assertEquals(List.of("https://store.example.test/repository/plugin-store-releases/plugins/demo/one.jar",
                "https://store.example.test/repository/plugin-store-releases/plugins/demo/two.jar"),
                List.of(first.jar().url(), second.jar().url()));
    }

    @Test
    void fetchDescriptorRejectsForeignUrlsAndInvalidDescriptors() {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 200, "application/json",
                descriptor("demo", "1.0.0", "jar.jar", "not-a-hash", null, null).getBytes(StandardCharsets.UTF_8)));
        JdkPluginStoreGateway gateway = gateway(ROOT, client);

        assertThrows(BizException.class, () -> gateway.fetchDescriptor(ref(),
                "https://evil.example.test/repository/plugin-store-releases/plugins/demo/index.json", DEMO_DESCRIPTOR_URL));
        assertThrows(BizException.class, () -> gateway.fetchDescriptor(ref(), DEMO_INDEX_URL,
                "https://evil.example.test/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json"));
        assertTrue(client.requests.isEmpty());

        assertThrows(BizException.class, () -> gateway.fetchDescriptor(ref(), DEMO_INDEX_URL, DEMO_DESCRIPTOR_URL));
        assertEquals(List.of(DEMO_DESCRIPTOR_URL), client.requests.stream().map(URI::toString).toList());
    }

    @Test
    void parseDescriptorRejectsInvalidDescriptorsWithoutHttp() {
        JdkPluginStoreGateway gateway = gateway(ROOT, new FakeHttpClient(request -> {
            throw new AssertionError("parseDescriptor must not request anything");
        }));

        assertThrows(BizException.class, () -> gateway.parseDescriptor(ref(), DEMO_INDEX_URL,
                descriptor("demo", "1.0.0", "jar.jar", "not-a-hash", null, null)));
        assertThrows(BizException.class, () -> gateway.parseDescriptor(ref(), DEMO_INDEX_URL,
                descriptorWithPluginFields("\"compatibility\":{\"host\":\"^1.0.0 || ^2.0.0\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"^1.0.0\"}")));
        assertThrows(BizException.class, () -> gateway.parseDescriptor(ref(),
                "https://evil.example.test/repository/plugin-store-releases/plugins/demo/index.json",
                descriptor("demo", "1.0.0", "jar.jar", SHA_256, null, null)));
    }

    @Test
    void parseDescriptorParsesCompatibilityAndDependencies() {
        String descriptor = "{\"schemaVersion\":1,\"releaseVersion\":\"1.0.0\",\"plugin\":{\"code\":\"demo\",\"version\":\"1.0.0\",\"main\":\"example.Plugin\","
                + "\"compatibility\":{\"host\":\">=1.0.0 <2.0.0\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"~1.0.0\"},"
                + "\"dependencies\":[{\"code\":\"base\",\"range\":\"1.2.x\",\"required\":true},{\"code\":\"optional\",\"range\":\"^9.0.0\",\"required\":false}]},"
                + "\"jar\":{\"mavenCoordinates\":\"g:a:1.0.0\",\"url\":\"jar.jar\",\"sha256\":\"" + SHA_256 + "\"}}";
        PluginStorePluginDescriptor result = parsedDescriptor(descriptor);

        assertEquals(">=1.0.0 <2.0.0", result.compatibility().host());
        assertEquals("^2.6.0", result.compatibility().spi());
        assertEquals("~1.0.0", result.compatibility().frontendSdk());
        assertEquals(List.of("base", "optional"), result.dependencies().stream().map(dependency -> dependency.code()).toList());
        assertTrue(result.dependencies().getFirst().required());
        assertFalse(result.dependencies().get(1).required());
    }

    @Test
    void parseDescriptorParsesCompatibilityFieldSubsets() {
        PluginStorePluginDescriptor hostOnly = parsedDescriptor(descriptorWithPluginFields("\"compatibility\":{\"host\":\"^1.0.0\"}"));
        assertEquals("^1.0.0", hostOnly.compatibility().host());
        assertNull(hostOnly.compatibility().spi());
        assertNull(hostOnly.compatibility().frontendSdk());

        PluginStorePluginDescriptor empty = parsedDescriptor(descriptorWithPluginFields("\"compatibility\":{}"));
        assertNull(empty.compatibility().host());
        assertNull(empty.compatibility().spi());
        assertNull(empty.compatibility().frontendSdk());
    }

    @Test
    void parseDescriptorRejectsInvalidSemanticVersionsCompatibilityAndDependencies() {
        assertThrows(BizException.class, () -> parsedDescriptor("""
                {"schemaVersion":1,"releaseVersion":"1.0.0-beta","plugin":{"code":"demo","version":"1.0.0-beta","main":"example.Plugin"},
                "jar":{"mavenCoordinates":"g:a:1.0.0","url":"jar.jar","sha256":"%s"}}""".formatted(SHA_256)));
        for (String invalidCompatibility : new String[]{"^1.0.0 || ^2.0.0", "[1.0.0,2.0.0)", "1.0.0-beta", ">=2.0.0 <1.0.0"}) {
            assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(
                    "\"compatibility\":{\"host\":\"" + invalidCompatibility + "\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"^1.0.0\"}")));
        }
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"compatibility\":{\"host\":\"\"}")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"compatibility\":{\"host\":true}")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(
                "\"compatibility\":{\"host\":\"^1.0.0\",\"spi\":\"^2.6.0\",\"frontendSdk\":\"^1.0.0\",\"extra\":true}")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":\"true\"}]")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":true,\"extra\":true}]")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\"}]")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields("\"dependencies\":[{\"code\":\"base\",\"range\":\">=2.0.0 <1.0.0\",\"required\":true}]")));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(
                "\"dependencies\":[{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":true},{\"code\":\"base\",\"range\":\"^1.0.0\",\"required\":false}]")));
    }

    @Test
    void parseDescriptorParsesOptionalDisplayMetadataWithoutAffectingJarContract() {
        PluginStorePluginDescriptor result = parsedDescriptor("""
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
    void parseDescriptorKeepsLegacyDescriptorDisplayMetadataAbsent() {
        PluginStorePluginDescriptor result = parsedDescriptor(descriptor("demo", "1.0.0", "jar.jar", SHA_256, null, null));

        assertNull(result.publisher());
        assertNull(result.source());
        assertNull(result.license());
        assertNull(result.releaseNotes());
    }

    @Test
    void parseDescriptorRejectsUnsafeOrMalformedDisplayMetadata() {
        String valid = "\"publisher\":{\"id\":\"publisher\",\"name\":\"Publisher\",\"url\":\"https://publisher.example\",\"verified\":false},"
                + "\"source\":{\"repository\":\"https://github.com/example/demo\",\"commit\":\"0123456789abcdef0123456789abcdef01234567\"},"
                + "\"license\":\"MIT\",\"releaseNotes\":\"Notes\"";
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("https://publisher.example", "http://publisher.example"))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("https://publisher.example", "https://user@publisher.example"))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("https://github.com/example/demo", "https://github.com/example/demo#fragment"))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("0123456789abcdef0123456789abcdef01234567", "0123456789abcdef0123456789abcdef0123456G"))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("\"verified\":false", "\"verified\":\"false\""))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("\"license\":\"MIT\"", "\"license\":\"MIT OR Apache-2.0\""))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("\"releaseNotes\":\"Notes\"", "\"releaseNotes\":\"line\nfeed\""))));
        assertThrows(BizException.class, () -> parsedDescriptor(descriptorWithPluginFields(valid.replace("\"verified\":false", "\"verified\":false,\"artifactUrl\":\"https://evil.example/plugin.jar\""))));
    }

    @Test
    void fetchCatalogRejectsDescriptorVersionMismatchWithIndex() {
        FakeHttpClient client = new FakeHttpClient(request -> response(request, switch (request.uri().getPath()) {
            case "/repository/plugin-store-releases/index.json" -> """
                    {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                    """;
            case "/repository/plugin-store-releases/plugins/demo/index.json" -> validIndex();
            case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor("demo", "2.0.0", "jar.jar", SHA_256, null, null);
            default -> throw new AssertionError("Unexpected request: " + request.uri());
        }));

        assertTrue(gateway(ROOT, client).fetchCatalog(ref()).isEmpty());
    }

    @Test
    void sendsBearerTokenOnlyWhenConfigured() {
        Function<HttpRequest, HttpResponse<java.io.InputStream>> responder = request -> response(request,
                switch (request.uri().getPath()) {
                    case "/repository/plugin-store-releases/index.json" -> """
                            {"schemaVersion":1,"plugins":[{"code":"demo","index":"plugins/demo/index.json"}]}
                            """;
                    case "/repository/plugin-store-releases/plugins/demo/index.json" -> validIndex();
                    case "/repository/plugin-store-releases/plugins/demo/versions/1.0.0.json" -> descriptor("demo", "1.0.0", "jar.jar", SHA_256, null, null);
                    default -> throw new AssertionError("Unexpected request: " + request.uri());
                });

        FakeHttpClient withToken = new FakeHttpClient(responder);
        gateway(ROOT, withToken).fetchCatalog(new PluginStoreSourceRef(ROOT.toString(), "secret-token"));
        assertTrue(withToken.sent.stream().allMatch(request -> request.headers().firstValue("Authorization")
                .orElse("").equals("Bearer secret-token")));

        FakeHttpClient withoutToken = new FakeHttpClient(responder);
        gateway(ROOT, withoutToken).fetchCatalog(ref());
        assertTrue(withoutToken.sent.stream().allMatch(request -> request.headers().firstValue("Authorization").isEmpty()));
    }

    @Test
    void downloadsJarToProvidedTargetAfterValidatingHashAndSize() throws IOException {
        byte[] jar = "plugin-jar".getBytes(StandardCharsets.UTF_8);
        Path target = Files.createTempFile("plugin-store-", ".jar");
        Files.deleteIfExists(target);
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 200, "application/java-archive", jar));

        gateway(ROOT, client).downloadJar(null, storeDescriptor("https://store.example.test/repository/maven/demo.jar", sha256(jar)), target);

        assertEquals("plugin-jar", Files.readString(target));
        assertEquals(List.of(URI.create("https://store.example.test/repository/maven/demo.jar")), client.requests);
        Files.deleteIfExists(target);
    }

    @Test
    void rejectsUnsafeOrInvalidJarDownloadsWithoutLeavingTarget() throws IOException {
        Path target = Files.createTempFile("plugin-store-", ".jar");
        Files.deleteIfExists(target);
        FakeHttpClient client = new FakeHttpClient(request -> response(request, 302, "application/java-archive", new byte[0]));

        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(null,
                storeDescriptor("http://store.example.test/demo.jar", SHA_256), target));
        assertFalse(Files.exists(target));
        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(null,
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

        assertThrows(BizException.class, () -> gateway.downloadJar(null,
                storeDescriptor("https://store.example.test/demo.jar", sha256(jar)), target));
        assertFalse(Files.exists(target));
        assertThrows(BizException.class, () -> gateway(ROOT, client).downloadJar(null,
                storeDescriptor("https://store.example.test/demo.jar", SHA_256), target));
        assertFalse(Files.exists(target));
    }

    private PluginStorePluginDescriptor parsedDescriptor(String descriptor) {
        return gateway(ROOT, new FakeHttpClient(request -> {
            throw new AssertionError("Unexpected request: " + request.uri());
        })).parseDescriptor(ref(), DEMO_INDEX_URL, descriptor);
    }

    private static PluginStoreSourceRef ref() {
        return new PluginStoreSourceRef(ROOT.toString(), null);
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
        private final List<HttpRequest> sent = new ArrayList<>();
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
            sent.add(request);
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responder.apply(request);
            return response;
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
    }
}
