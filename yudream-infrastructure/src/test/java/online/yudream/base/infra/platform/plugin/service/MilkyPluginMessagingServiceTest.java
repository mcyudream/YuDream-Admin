package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageResult;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkyPluginMessagingServiceTest {

    @Test
    void serializesCompositeAndAudioMessagesToMilkySegments() {
        AtomicReference<String> api = new AtomicReference<>();
        AtomicReference<Map<String, Object>> payload = new AtomicReference<>();
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(connection);
                    case "findEnabled" -> List.of(connection);
                    default -> null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    api.set((String) args[1]);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) args[2];
                    payload.set(body);
                    return Map.of("message_seq", 42);
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        service.sendToChannel("1", "1064685901", new PluginMessageContent(PluginMessageContent.Type.COMPOSITE,
                "{\"messages\":[{\"user_id\":2675448709,\"sender_name\":\"评论\",\"segments\":[{\"type\":\"text\",\"data\":{\"text\":\"内容\"}}]}]}", List.of(), Map.of())).toCompletableFuture().join();
        assertEquals("send_group_message", api.get());
        assertEquals("1064685901", payload.get().get("group_id"));
        assertEquals("forward", ((Map<?, ?>) ((List<?>) payload.get().get("message")).getFirst()).get("type"));

        service.sendToChannel("1", "1064685901", new PluginMessageContent(PluginMessageContent.Type.AUDIO,
                "https://audio.example.test/post.mp3", List.of(), Map.of())).toCompletableFuture().join();
        Map<?, ?> record = (Map<?, ?>) ((List<?>) payload.get().get("message")).getFirst();
        assertEquals("record", record.get("type"));
        assertEquals("https://audio.example.test/post.mp3", ((Map<?, ?>) record.get("data")).get("uri"));

        service.sendToChannel("1", "1064685901", new PluginMessageContent(PluginMessageContent.Type.IMAGE,
                "base64://aW1hZ2U=", List.of(), Map.of())).toCompletableFuture().join();
        Map<?, ?> image = (Map<?, ?>) ((List<?>) payload.get().get("message")).getFirst();
        assertEquals("image", image.get("type"));
        assertEquals("base64://aW1hZ2U=", ((Map<?, ?>) image.get("data")).get("uri"));
    }

    @Test
    void routesOfficialChannelAndPrivateScenesFromReferrer() {
        AtomicReference<String> api = new AtomicReference<>();
        AtomicReference<Map<String, Object>> payload = new AtomicReference<>();
        MilkyConnection connection = MilkyConnection.create("Official", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(connection);
                    case "findEnabled" -> List.of(connection);
                    default -> null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    api.set((String) args[1]);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) args[2];
                    payload.set(body);
                    return Map.of("id", "official-msg");
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        service.send(new online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest(
                "1", "qq", "bot", "ch-1",
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "hello channel", List.of(),
                        Map.of("message_scene", "channel", "msg_id", "inbound-ch", "event_id", "evt-ch"))))
                .toCompletableFuture().join();
        assertEquals("send_channel_message", api.get());
        assertEquals("ch-1", payload.get().get("channel_id"));
        assertEquals("inbound-ch", payload.get().get("msg_id"));
        assertEquals("evt-ch", payload.get().get("event_id"));

        service.send(new online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest(
                "1", "qq", "bot", "user-open",
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "hello friend", List.of(),
                        Map.of("message_scene", "friend", "message_id", "p1"))))
                .toCompletableFuture().join();
        assertEquals("send_private_message", api.get());
        assertEquals("user-open", payload.get().get("user_id"));
        assertEquals("p1", payload.get().get("msg_id"));
    }

    @Test
    void acceptsSuccessfulMilkyResponsesWithoutData() {
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(connection);
                    case "findEnabled" -> List.of(connection);
                    default -> null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> null);
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        service.sendToChannel("1", "1064685901", new PluginMessageContent(PluginMessageContent.Type.COMPOSITE,
                "{\"messages\":[]}", List.of(), Map.of())).toCompletableFuture().join();
    }

    @Test
    void listsConnectionsWithoutCallingTheRemoteMilkyApi() {
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findEnabled".equals(method.getName()) ? List.of(connection) : null);
        AtomicInteger calls = new AtomicInteger();
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    calls.incrementAndGet();
                    return Map.of();
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        var connections = service.connections();

        assertEquals(1, connections.size());
        assertEquals("1", connections.getFirst().id());
        assertEquals("qq", connections.getFirst().platform());
        assertEquals("milky", connections.getFirst().protocol());
        assertNull(connections.getFirst().userId());
        assertEquals(0, calls.get());
    }

    @Test
    void listsOfficialConnectionsWithOfficialProtocol() {
        MilkyConnection connection = MilkyConnection.create(
                "官Q", "official", null, null, "app-id", "app-secret", false, null, "base64", null);
        connection.setId(9L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findEnabled".equals(method.getName()) ? List.of(connection) : null);
        AtomicInteger calls = new AtomicInteger();
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    calls.incrementAndGet();
                    return Map.of();
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        var connections = service.connections();

        assertEquals(1, connections.size());
        assertEquals("9", connections.getFirst().id());
        assertEquals("官Q", connections.getFirst().name());
        assertEquals("qq", connections.getFirst().platform());
        assertEquals("official", connections.getFirst().protocol());
        assertEquals(0, calls.get());
    }

    @Test
    void listsGroupsWithoutFailingWhenOfficialCacheIsEmpty() {
        MilkyConnection connection = MilkyConnection.create(
                "官Q", "official", null, null, "app-id", "app-secret", false, null, "base64", null);
        connection.setId(9L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findById".equals(method.getName()) ? Optional.of(connection) : null);
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("gateway unavailable");
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        assertTrue(service.groups("9").isEmpty());
    }

    @Test
    void runsBlockingMilkyCallsOnTheNamedExecutor() {
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findById".equals(method.getName()) ? Optional.of(connection) : null);
        AtomicReference<String> threadName = new AtomicReference<>();
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    threadName.set(Thread.currentThread().getName());
                    return Map.of("message_seq", 42);
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        service.sendToChannel("1", "1064685901", new PluginMessageContent(PluginMessageContent.Type.TEXT,
                "private message", List.of(), Map.of())).toCompletableFuture().join();
        service.shutdown();

        assertTrue(threadName.get().startsWith("milky-plugin-messaging-"));
    }

    @Test
    void capturesSandboxMessagingWithoutRepositoryOrGatewayCalls() {
        AtomicInteger repositoryCalls = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> {
                    repositoryCalls.incrementAndGet();
                    return null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    gatewayCalls.incrementAndGet();
                    return null;
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());
        QqSandboxSession session = QqSandboxSession.create("sandbox", "demo", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.REAL, 1_000L, java.time.Instant.now());

        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            service.sendToChannel(session.connectionId(), session.channelId(),
                    new PluginMessageContent(PluginMessageContent.Type.TEXT, "captured", List.of(), Map.of()))
                    .toCompletableFuture().join();
            service.invoke(session.connectionId(), "send_group_message", Map.of("group_id", session.channelId()))
                    .toCompletableFuture().join();
        }

        assertEquals(0, repositoryCalls.get());
        assertEquals(0, gatewayCalls.get());
        assertTrue(session.timeline().stream().anyMatch(event -> "messaging.sendToChannel".equals(event.action())));
        assertTrue(session.timeline().stream().anyMatch(event -> "messaging.raw.invoke".equals(event.action())));
    }

    @Test
    void capturesAsyncSandboxReplyWithoutThreadLocalAndTracksAgentActivity() {
        AtomicInteger repositoryCalls = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> {
                    repositoryCalls.incrementAndGet();
                    return null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    gatewayCalls.incrementAndGet();
                    return null;
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());
        InMemoryQqSandboxSessionRepo sessions = new InMemoryQqSandboxSessionRepo();
        service.setSandboxSessions(sessions);
        QqSandboxSession session = QqSandboxSession.create("async", "ai-chatbot", "1", "2", "3", null, "4", "group",
                QqSandboxRandomMode.FORCE_HIT, 1_000L, java.time.Instant.now());
        sessions.save(session);

        service.invoke(session.connectionId(), "devtools_sandbox_diagnostic",
                Map.of("milestone", "agent_pending", "traceId", "trace-1")).toCompletableFuture().join();
        assertTrue(session.hasActiveOperations());
        service.sendToChannel(session.connectionId(), session.channelId(),
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "async reply", List.of(), Map.of()))
                .toCompletableFuture().join();
        service.invoke(session.connectionId(), "devtools_sandbox_diagnostic",
                Map.of("milestone", "agent_complete", "traceId", "trace-1")).toCompletableFuture().join();

        assertEquals(0, repositoryCalls.get());
        assertEquals(0, gatewayCalls.get());
        assertEquals(false, session.hasActiveOperations());
        assertTrue(session.timeline().stream().anyMatch(event -> "messaging.sendToChannel".equals(event.action())
                && "async reply".equals(event.payload().get("content"))));
    }

    @Test
    void preservesAsyncGatewayFailureForCallers() {
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findById".equals(method.getName()) ? Optional.of(connection) : null);
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("gateway unavailable");
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.sendToChannel("1", "1064685901",
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "private message", List.of(), Map.of())).toCompletableFuture().join());
    }

    @Test
    void officialTextFallsBackToMarkdownWithKeyboardAndInlineImages() {
        AtomicReference<String> api = new AtomicReference<>();
        AtomicReference<Map<String, Object>> payload = new AtomicReference<>();
        MilkyConnection connection = MilkyConnection.create("Official", "official", null, null,
                "app-id", "app-secret", false, null, null, null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(connection);
                    case "findEnabled" -> List.of(connection);
                    default -> null;
                });
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> {
                    api.set((String) args[1]);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) args[2];
                    payload.set(body);
                    return Map.of("id", "official-msg");
                });
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        PluginMessageContent content = new PluginMessageContent(PluginMessageContent.Type.TEXT,
                "结果见下图\n[[shot]]", List.of(new PluginMessageContent.Attachment("https://img.example.test/a.png", "shot", "image/png")),
                Map.of("msg_id", "inbound-1"),
                List.of(PluginMessageContent.Button.command("b1", "再来一局", "/再来一局"),
                        PluginMessageContent.Button.callback("b2", "查看详情", "detail:1")));
        PluginMessageResult result = service.sendToChannel("1", "1064685901", content).toCompletableFuture().join();

        assertEquals("send_group_message", api.get());
        assertEquals(2, payload.get().get("msg_type"));
        assertEquals("inbound-1", payload.get().get("msg_id"));
        String markdown = String.valueOf(((Map<?, ?>) payload.get().get("markdown")).get("content"));
        assertTrue(markdown.contains("结果见下图\n![shot](https://img.example.test/a.png)"));
        List<?> rows = (List<?>) ((Map<?, ?>) ((Map<?, ?>) payload.get().get("keyboard")).get("content")).get("rows");
        List<?> firstRow = (List<?>) ((Map<?, ?>) rows.getFirst()).get("buttons");
        Map<?, ?> command = (Map<?, ?>) firstRow.get(0);
        assertEquals("b1", command.get("id"));
        assertEquals(2, ((Map<?, ?>) command.get("action")).get("type"));
        assertEquals("/再来一局", ((Map<?, ?>) command.get("action")).get("data"));
        Map<?, ?> callback = (Map<?, ?>) firstRow.get(1);
        assertEquals(1, ((Map<?, ?>) callback.get("action")).get("type"));
        assertEquals(false, result.degraded());
        assertEquals(List.of("official-msg"), result.messageIds());
    }

    @Test
    void milkySendMarksButtonOnlyMessagesAsDegraded() {
        MilkyConnection connection = MilkyConnection.create("Milky", "http://127.0.0.1:3000", "token", "base64", null);
        connection.setId(1L);
        MilkyConnectionRepo repository = (MilkyConnectionRepo) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyConnectionRepo.class}, (proxy, method, args) -> "findById".equals(method.getName()) ? Optional.of(connection) : null);
        MilkyApiGateway gateway = (MilkyApiGateway) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{MilkyApiGateway.class}, (proxy, method, args) -> Map.of("message_seq", 7));
        PluginUserService users = (PluginUserService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PluginUserService.class}, (proxy, method, args) -> null);
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(repository, gateway, users, new ObjectMapper());

        PluginMessageResult result = service.sendToChannel("1", "1064685901",
                new PluginMessageContent(PluginMessageContent.Type.TEXT, "带按钮的文本", List.of(), Map.of(),
                        List.of(PluginMessageContent.Button.command("b1", "/菜单", "/菜单")))).toCompletableFuture().join();
        assertTrue(result.degraded());
    }
}
