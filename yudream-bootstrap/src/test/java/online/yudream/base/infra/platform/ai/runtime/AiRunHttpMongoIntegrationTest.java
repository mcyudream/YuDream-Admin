package online.yudream.base.infra.platform.ai.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.agentscope.core.message.*;
import io.agentscope.core.model.*;
import jakarta.servlet.Filter;
import online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO;
import online.yudream.base.application.platform.ai.service.*;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import online.yudream.base.infra.platform.ai.impl.AiRunRepoImpl;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.*;
import online.yudream.base.infra.platform.chat.impl.ChatQuotaRepoImpl;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.controller.AiRunController;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Opt-in socket/Mongo integration. Requires an explicitly owned, disposable local Docker Mongo.
 * Uses real controllers, SSE, native AgentSession and persistent quota; model and authentication
 * are synthetic fixture boundaries. This does not verify a real model supplier or production login.
 */
@EnabledIfSystemProperty(named = "yudream.ai.httpE2e", matches = "true")
class AiRunHttpMongoIntegrationTest {
    private static final List<String> PERMISSIONS = List.of("platform:chat:use");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static Fixture current;

    @Test
    void socketDisconnectDoesNotCancelNativeRunAndNewHttpInstanceRespondsFromMongo() throws Exception {
        OwnedMongo owned = OwnedMongo.verify();
        try (Fixture fixture = new Fixture(owned); SocketHttp client = new SocketHttp()) {
            current = fixture;
            long observedCursor;
            String runId;
            String originalTurn;
            long waitingCursor;
            try (HttpApp first = HttpApp.start()) {
                var receipt = data(post(client, first, "/fixture/runs", Map.of()));
                runId = receipt.path("id").asText();
                assertFalse(runId.isBlank());
                var stream = client.stream(first, "/api/platform/ai/runs/" + runId + "/events?after=0");
                assertEquals(200, stream.statusCode());
                assertTrue(stream.contentType().startsWith("text/event-stream"));
                try (InputStream body = stream.body()) {
                    SseEvent firstDelta = readUntil(body, event -> "delta".equals(event.data().path("action").asText()));
                    assertEquals("首段", firstDelta.data().path("payload").path("content").asText());
                    observedCursor = firstDelta.cursor();
                    assertFalse(fixture.model.releaseTail.isDone(), "首段应在供应商边界完成前经真实 HTTP 到达");
                }
                var running = data(get(client, first, "/api/platform/ai/runs/" + runId));
                assertEquals("RUNNING", running.path("status").asText());
                assertFalse(fixture.repo.find(runId).orElseThrow().isCancelRequested());
                assertTrue(fixture.quotaRepo.findByUserAndDate(7L, LocalDate.now()).orElseThrow().getReservedTokens() > 0);
                fixture.model.releaseTail.complete(null);
                JsonNode waiting = awaitStatus(client, first, runId, "WAITING");
                assertEquals("首段后段", waiting.path("content").asText());
                assertEquals("模拟推理", waiting.path("reasoning").asText());
                waitingCursor = waiting.path("cursor").asLong();
                assertTrue(waitingCursor > observedCursor);
                assertEquals("external_execution", waiting.path("pendingInteractions").get(0).path("kind").asText());
                assertEquals("http-frontend-call", waiting.path("pendingInteractions").get(0).path("requestId").asText());
                originalTurn = fixture.repo.find(runId).orElseThrow().getNativeReference().turnId();
                assertEquals(1, fixture.model.calls.get());
                assertEquals(15, fixture.quotaRepo.findByUserAndDate(7L, LocalDate.now()).orElseThrow().getUsedTokens());
                assertEquals(403, getAs(client, first, "/api/platform/ai/runs/" + runId, "8").statusCode());

                var replay = client.stream(first, "/api/platform/ai/runs/" + runId + "/events?after=" + observedCursor);
                try (InputStream body = replay.body()) {
                    var events = readThrough(body, waitingCursor);
                    long previous = observedCursor;
                    for (var event : events) { assertTrue(event.cursor() > previous); previous = event.cursor(); }
                    assertEquals(waitingCursor, previous, "快照游标之后的重连只补齐已提交后缀");
                    assertTrue(events.stream().anyMatch(event -> "后段".equals(event.data().path("payload").path("content").asText())));
                    assertTrue(events.stream().anyMatch(event -> "ai.frontend".equals(event.data().path("event").asText())));
                }
            }

            // A fresh HTTP/service/runtime instance has only Mongo, not the original worker's closures.
            try (HttpApp restarted = HttpApp.start()) {
                var restored = data(get(client, restarted, "/api/platform/ai/runs/" + runId));
                assertEquals("WAITING", restored.path("status").asText());
                assertEquals(waitingCursor, restored.path("cursor").asLong());
                assertEquals(1, fixture.model.calls.get(), "恢复快照不能重新提交输入");
                assertEquals(200, post(client, restarted, "/api/platform/ai/runs/" + runId + "/frontend-tools/http-frontend-call",
                        Map.of("ok", true, "result", Map.of("routeName", "fixture"))).statusCode());
                var completed = awaitStatus(client, restarted, runId, "COMPLETED");
                assertEquals(runId, completed.path("id").asText());
                assertEquals("首段后段完成", completed.path("content").asText());
                assertEquals(43, completed.path("result").path("usage").path("totalTokens").asLong());
                assertEquals(originalTurn, fixture.repo.find(runId).orElseThrow().getNativeReference().turnId());
                assertEquals(2, fixture.model.calls.get());
                assertTrue(fixture.model.sawOriginalToolAnswer.get());
                assertEquals(1, fixture.repo.events(runId, 0, 256).stream().filter(event -> "ai.frontend".equals(event.event()) && "request".equals(event.action())).count());
                var quota = fixture.quotaRepo.findByUserAndDate(7L, LocalDate.now()).orElseThrow();
                assertEquals(43, quota.getUsedTokens());
                assertEquals(0, quota.getReservedTokens());
                assertEquals(0, quota.getUncertainTokens());
            }
        } finally { current = null; }
    }

    static final class Fixture implements AutoCloseable {
        final SyntheticModel model = new SyntheticModel();
        final MongoClient mongoClient;
        final MongoTemplate mongo;
        final Path workspace;
        final Map<String, String> config = Map.of("providers", """
                [{"code":"synthetic","type":"OPENAI_COMPATIBLE","baseUrl":"https://synthetic.invalid/v1",
                  "defaultModel":"synthetic","models":[{"code":"synthetic","model":"synthetic","kind":"chat"}]}]
                """, "dailyTokenLimit", "100000");
        final CapabilityModuleRepo modules;
        final CapabilityAppService capabilities;
        final IdGenerator ids = new IdGenerator() { final AtomicLong sequence = new AtomicLong(1000); public Long nextId() { return sequence.incrementAndGet(); } };
        AiRunRepoImpl repo;
        ChatQuotaRepoImpl quotaRepo;
        ChatQuotaAppService quota;
        HarnessAgentRuntime runtime;
        AiRunAppService runs;

        Fixture(OwnedMongo owned) throws IOException {
            workspace = Files.createTempDirectory("yudream-ai-http-e2e-");
            mongoClient = MongoClients.create("mongodb://127.0.0.1:" + owned.port() + "/?serverSelectionTimeoutMS=10000");
            mongo = new MongoTemplate(mongoClient, "ai_e2e_" + owned.id());
            mongo.executeCommand("{ping:1}");
            // 专属容器可重复验收：固定库名下的历史会话/额度数据会让新一轮 submit 排到旧挂起任务之后。
            mongo.getDb().drop();
            modules = mock(CapabilityModuleRepo.class);
            when(modules.findByCode("ai")).thenReturn(Optional.of(CapabilityModule.builder().code("ai").enabled(true).config(config).build()));
            capabilities = mock(CapabilityAppService.class);
        }

        void newServices() {
            repo = new AiRunRepoImpl(mongo);
            quotaRepo = new ChatQuotaRepoImpl(mongo, ids);
            quota = new ChatQuotaAppService(capabilities, modules, quotaRepo, repo);
            var clientProperties = new AiClientProperties();
            var properties = new HarnessRuntimeProperties(); properties.setWorkspaceDir(workspace.toString()); properties.setMemoryHooksEnabled(false);
            var models = new AgentScopeModels(clientProperties) {
                @Override public Model resolve(ResolvedAiModel ignored) { return model; }
            };
            var adapter = new AbstractOpenAiCompatibleProviderAdapter() { @Override public AiProviderType type() { return AiProviderType.OPENAI_COMPATIBLE; } };
            runtime = new HarnessAgentRuntime(models, new AiProviderConfigParser(), List.of(adapter), clientProperties, properties,
                    new MongoAgentStateStore(mongo), new MongoBaseStore(mongo), permission -> false,
                    AiRunHttpMongoIntegrationTest.<AiAgentTool>provider(), new MongoAgentSkillRepository(mongo));
            runtime.setUsageQuotaGateway(quota);
            runs = new AiRunAppService(repo, capabilities, JSON, runtime, modules, quota, List.of());
        }

        Object submit() {
            return AiRunWebAssembler.run(runs.start(7L, "fixture", "http-session", out -> {
                var route = new AiFrontendContextDTO.Route("fixture", "合成测试入口", "/fixture");
                var context = new AiFrontendContextDTO(route, List.of(route), List.of(), true, false);
                out.privateFrontendContext(context);
                try (var scoped = AiAgentToolExecutionScope.open(AiFrontendTools.create(context, out))) {
                    return runtime.run(new HarnessRunRequest(7L, "native-http-session", "general", "合成 HTTP 测试", "合成输入", List.of(),
                            "synthetic", "synthetic", config, true, PERMISSIONS, List.of(), List.of("ui.navigate")),
                            out::content, out::reasoning, out::tool, progress -> out.progress(progress.action(), progress));
                }
            }, () -> runtime.interrupt(7L, "native-http-session", "general")));
        }

        @Override public void close() throws IOException {
            try {
                model.releaseTail.complete(null);
                if (runs != null) for (var run : repo.list(7L, null, true)) { try { runs.cancel(7L, run.getId()); } catch (RuntimeException ignored) { } }
                mongoClient.close();
                Path root = workspace.toAbsolutePath().normalize();
                assertTrue(root.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()));
                assertTrue(root.getFileName().toString().startsWith("yudream-ai-http-e2e-"));
                try (var files = Files.walk(root)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
            } finally { mongoClient.close(); }
        }
    }

    static class SyntheticModel implements Model {
        final AtomicInteger calls = new AtomicInteger();
        final CompletableFuture<Void> releaseTail = new CompletableFuture<>();
        final AtomicBoolean sawOriginalToolAnswer = new AtomicBoolean();
        @Override public String getModelName() { return "synthetic"; }
        @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return Flux.concat(Flux.just(response(TextBlock.builder().text("首段").build(), null, null)),
                        Mono.fromFuture(releaseTail).thenMany(Flux.just(
                                response(ThinkingBlock.builder().thinking("模拟推理").build(), null, null),
                                response(TextBlock.builder().text("后段").build(), null, null),
                                response(ToolUseBlock.builder().id("http-frontend-call").name("ui_navigate").input(Map.of("routeName", "fixture")).build(),
                                        new ChatUsage(10, 5, 0.1), "tool_calls"))));
            }
            assertEquals(2, call, "断观察和恢复不能额外执行模型");
            sawOriginalToolAnswer.set(messages.stream().flatMap(message -> message.getContentBlocks(ToolResultBlock.class).stream())
                    .anyMatch(result -> "http-frontend-call".equals(result.getId()) && result.getOutput().stream()
                            .anyMatch(block -> block instanceof TextBlock text && text.getText().contains("\"ok\":true"))));
            return Flux.just(response(TextBlock.builder().text("完成").build(), new ChatUsage(20, 8, 0.1), "stop"));
        }
        private ChatResponse response(ContentBlock content, ChatUsage usage, String finish) { return new ChatResponse("synthetic-response", List.of(content), usage, Map.of(), finish); }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class,
            org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
            org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class})
    @Import({AiRunController.class, AiRunStreamSupport.class, SubmissionController.class, FixtureErrors.class})
    static class FixtureConfig {
        @Bean static org.springframework.boot.convert.ApplicationConversionService conversionService() {
            return new org.springframework.boot.convert.ApplicationConversionService();
        }
        @Bean ObjectMapper objectMapper() { return JSON; }
        @Bean AiRunAppService runs() { return current.runs; }
        @Bean ServletWebServerFactory webServer() throws Exception {
            var factory = new TomcatServletWebServerFactory(0);
            // Actual TCP/Servlet async I/O; NIO2 avoids this Windows JDK's broken AF_UNIX selector pipe.
            factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
            factory.setAddress(InetAddress.getByName("127.0.0.1")); return factory;
        }
        @Bean FilterRegistrationBean<Filter> syntheticActor() {
            Filter filter = (request, response, chain) -> {
                String actor = ((jakarta.servlet.http.HttpServletRequest) request).getHeader("X-Fixture-Actor");
                if (!Set.of("7", "8").contains(actor == null ? "" : actor)) { ((jakarta.servlet.http.HttpServletResponse) response).setStatus(401); return; }
                ApiKeyAuthenticationContext.set(new ApiKeyAuthentication(1L, Long.valueOf(actor), PERMISSIONS));
                try { chain.doFilter(request, response); } finally { ApiKeyAuthenticationContext.clear(); }
            };
            var registration = new FilterRegistrationBean<>(filter); registration.setOrder(-100); return registration;
        }
    }

    @RestController static class SubmissionController {
        @PostMapping("/fixture/runs") Object start() {
            assertEquals(7L, SecurityPrincipalSupport.current().userId());
            return Result.ok(current.submit());
        }
    }
    @RestControllerAdvice static class FixtureErrors {
        @ExceptionHandler(BizException.class) @ResponseStatus(HttpStatus.FORBIDDEN) Map<String, String> rejected() { return Map.of("error", "fixture request rejected"); }
    }

    record HttpApp(AnnotationConfigServletWebServerApplicationContext context, URI base) implements AutoCloseable {
        static HttpApp start() {
            current.newServices();
            var context = new AnnotationConfigServletWebServerApplicationContext();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-fixture", Map.of(
                    "server.port", "0", "server.address", "127.0.0.1", "yudream.platform.capabilities.ai.enabled", "true",
                    "yudream.platform.ai.client.sse-timeout", "60s", "spring.main.banner-mode", "off")));
            context.register(FixtureConfig.class); context.refresh();
            int port = context.getWebServer().getPort();
            assertTrue(port > 0 && port != 8080 && port != 8081);
            return new HttpApp(context, URI.create("http://127.0.0.1:" + port));
        }
        @Override public void close() { context.close(); }
    }

    record OwnedMongo(String id, int port) {
        static OwnedMongo verify() throws Exception {
            String id = System.getProperty("yudream.ai.e2e.owner", "");
            String container = System.getProperty("yudream.ai.e2e.container", "");
            assertTrue(id.matches("[a-f0-9]{32}")); assertEquals("yudream-ai-e2e-" + id, container);
            Path output = Files.createTempFile("yudream-ai-e2e-inspect-", ".json");
            try {
                var process = new ProcessBuilder("docker", "inspect", container)
                        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly(); fail("本次临时容器的归属检查超时");
                }
                assertEquals(0, process.exitValue());
                var inspect = JSON.readTree(output.toFile()).get(0);
                assertEquals(id, inspect.path("Config").path("Labels").path("yudream.codex.ai-e2e").asText());
                var binding = inspect.path("NetworkSettings").path("Ports").path("27017/tcp").get(0);
                assertEquals("127.0.0.1", binding.path("HostIp").asText());
                int port = Integer.parseInt(binding.path("HostPort").asText()); assertTrue(port > 1024 && port != 27017 && port != 6379);
                return new OwnedMongo(id, port);
            } finally { Files.deleteIfExists(output); }
        }
    }

    record SseEvent(long cursor, JsonNode data) { }
    static SseEvent readUntil(InputStream body, java.util.function.Predicate<SseEvent> match) throws Exception {
        return readStream(body, match, null).getLast();
    }
    static List<SseEvent> readThrough(InputStream body, long cursor) throws Exception { return readStream(body, event -> event.cursor() >= cursor, new ArrayList<>()); }
    static List<SseEvent> readStream(InputStream body, java.util.function.Predicate<SseEvent> match, List<SseEvent> collected) throws Exception {
        var complete = new CompletableFuture<List<SseEvent>>();
        Thread.startVirtualThread(() -> {
            var events = collected == null ? new ArrayList<SseEvent>() : collected;
            try {
                var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                String id = null; String data = null; String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("id:")) id = line.substring(3).trim();
                    if (line.startsWith("data:")) data = line.substring(5).trim();
                    if (line.isEmpty() && id != null && data != null) {
                        var event = new SseEvent(Long.parseLong(id), JSON.readTree(data)); events.add(event);
                        if (match.test(event)) { complete.complete(events); return; }
                        id = null; data = null;
                    }
                }
                complete.completeExceptionally(new AssertionError("SSE 在目标事件之前结束"));
            } catch (Throwable error) { complete.completeExceptionally(error); }
        });
        return complete.get(20, TimeUnit.SECONDS);
    }
    record Reply<T>(int statusCode, T body, String contentType) { }

    /** A real TCP client with no proxy, redirects, selector or mocked HTTP dispatch. */
    static final class SocketHttp implements AutoCloseable {
        final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
        HttpURLConnection connect(HttpApp app, String path, String actor, byte[] body) throws IOException {
            URI uri = app.base().resolve(path);
            assertEquals("127.0.0.1", uri.getHost()); assertEquals(app.base().getPort(), uri.getPort());
            var connection = (HttpURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
            connections.add(connection);
            connection.setConnectTimeout(20_000); connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("X-Fixture-Actor", actor);
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(body.length);
                try (var output = connection.getOutputStream()) { output.write(body); }
            }
            return connection;
        }
        Reply<String> exchange(HttpApp app, String path, String actor, byte[] body) throws IOException {
            var connection = connect(app, path, actor, body);
            try {
                int code = connection.getResponseCode();
                try (var input = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                    return new Reply<>(code, input == null ? "" : new String(input.readAllBytes(), StandardCharsets.UTF_8),
                            connection.getContentType());
                }
            } finally { connection.disconnect(); connections.remove(connection); }
        }
        Reply<InputStream> stream(HttpApp app, String path) throws IOException {
            var connection = connect(app, path, "7", null);
            int code = connection.getResponseCode();
            var input = new FilterInputStream(connection.getInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { connection.disconnect(); connections.remove(connection); }
                }
            };
            return new Reply<>(code, input, connection.getContentType());
        }
        @Override public void close() { for (var connection : connections) connection.disconnect(); connections.clear(); }
    }
    static Reply<String> get(SocketHttp client, HttpApp app, String path) throws Exception { return getAs(client, app, path, "7"); }
    static Reply<String> getAs(SocketHttp client, HttpApp app, String path, String actor) throws Exception {
        return client.exchange(app, path, actor, null);
    }
    static Reply<String> post(SocketHttp client, HttpApp app, String path, Object value) throws Exception {
        return client.exchange(app, path, "7", JSON.writeValueAsBytes(value));
    }
    static JsonNode data(Reply<String> response) throws Exception { assertEquals(200, response.statusCode(), response.body()); return JSON.readTree(response.body()).path("data"); }
    static JsonNode awaitStatus(SocketHttp client, HttpApp app, String id, String expected) throws Exception {
        Instant deadline = Instant.now().plusSeconds(20); JsonNode latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest = data(get(client, app, "/api/platform/ai/runs/" + id));
            if (expected.equals(latest.path("status").asText())) return latest;
            if (Set.of("FAILED", "CANCELLED").contains(latest.path("status").asText())) fail("隔离运行意外结束：" + latest.path("error").asText());
            Thread.sleep(50);
        }
        fail("隔离运行没有到达 " + expected + "，最后状态：" + (latest == null ? "missing" : latest.path("status").asText())); return null;
    }
    @SafeVarargs static <T> ObjectProvider<T> provider(T... values) {
        return new ObjectProvider<>() { @Override public T getObject() { return values.length == 0 ? null : values[0]; } @Override public Stream<T> stream() { return Arrays.stream(values); } };
    }
}
