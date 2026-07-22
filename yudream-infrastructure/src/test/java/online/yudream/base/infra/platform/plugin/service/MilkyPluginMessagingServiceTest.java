package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
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
        assertNull(connections.getFirst().userId());
        assertEquals(0, calls.get());
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
}
