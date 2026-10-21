package online.yudream.base.infra.system.user.service;

import online.yudream.base.domain.system.user.valobj.InitialPasswordSetupTarget;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialPasswordSetupTokenServiceTest {

    @Test
    void generateRejectsBlankTargets() {
        TestStore store = new TestStore();
        InitialPasswordSetupTokenService service = store.service();
        assertThrows(IllegalArgumentException.class, () -> service.generate(null, "user@site.com"));
        assertThrows(IllegalArgumentException.class, () -> service.generate(1L, " "));
        assertThrows(IllegalArgumentException.class, () -> service.generate(1L, null));
        assertTrue(store.data.isEmpty());
    }

    @Test
    void generateStoresUserIdAndEmailUnderDedicatedPrefixWithTtl() {
        TestStore store = new TestStore();
        InitialPasswordSetupTokenService service = store.service();
        String first = service.generate(7L, "newuser@site.com");
        String second = service.generate(8L, "someone@site.com");
        assertNotEquals(first, second);
        assertEquals("7|newuser@site.com", store.data.get("password:initial-setup:" + first));
        assertEquals("8|someone@site.com", store.data.get("password:initial-setup:" + second));
        assertEquals(Duration.ofHours(24), store.ttl.get("password:initial-setup:" + first));
        assertTrue(store.data.keySet().stream().allMatch(key -> key.startsWith("password:initial-setup:")),
                "初始设置 token 不得落入普通重置前缀");
    }

    @Test
    void validateParsesTokenAndReturnsEmptyForUnknownOrBlank() {
        TestStore store = new TestStore();
        InitialPasswordSetupTokenService service = store.service();
        String token = service.generate(9007199254740993L, "user@site.com");
        Optional<InitialPasswordSetupTarget> parsed = service.validate(token);
        assertEquals(9007199254740993L, parsed.orElseThrow().userId());
        assertEquals("user@site.com", parsed.orElseThrow().email());
        assertFalse(service.validate("missing-token").isPresent());
        assertFalse(service.validate(" ").isPresent());
    }

    @Test
    void removeConsumesTokenOneTime() {
        TestStore store = new TestStore();
        InitialPasswordSetupTokenService service = store.service();
        String token = service.generate(1L, "user@site.com");
        assertTrue(service.validate(token).isPresent());
        service.remove(token);
        assertFalse(store.data.containsKey("password:initial-setup:" + token));
        assertFalse(service.validate(token).isPresent());
        assertDoesNotThrow(() -> service.remove("absent-token"));
        assertDoesNotThrow(() -> service.remove(null));
        assertDoesNotThrow(() -> service.remove(" "));
    }

    private static void assertDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            throw new AssertionError("不应抛出异常: " + e.getMessage(), e);
        }
    }

    /** 无 Mockito 的 infra 测试替身：StringRedisTemplate 子类 + ValueOperations 接口代理。 */
    private static class TestStore {
        final Map<String, String> data = new HashMap<>();
        final Map<String, Duration> ttl = new HashMap<>();

        InitialPasswordSetupTokenService service() {
            ValueOperations<String, String> ops = (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "set" -> {
                            data.put((String) args[0], (String) args[1]);
                            ttl.put((String) args[0], (Duration) args[2]);
                            yield null;
                        }
                        case "get" -> data.get(args[0]);
                        default -> throw new AssertionError(method.getName());
                    });
            StringRedisTemplate redis = new StringRedisTemplate() {
                @Override
                @SuppressWarnings(value = "unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return ops;
                }

                @Override
                public Boolean delete(String key) {
                    return data.remove(key) != null;
                }
            };
            return new InitialPasswordSetupTokenService(redis);
        }
    }
}
