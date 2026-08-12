package online.yudream.base.infra.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.security.service.ExternalLoginTicketStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedisExternalLoginTicketStore implements ExternalLoginTicketStore {
    private static final String STATE_PREFIX = "external-login:state:";
    private static final String BINDING_PREFIX = "external-login:binding:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration BINDING_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]); if value then redis.call('del', KEYS[1]); end; return value;",
            String.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveState(String token, State state) {
        stringRedisTemplate.opsForValue().set(STATE_PREFIX + token,
                encode(state.providerCode()) + "." + encode(state.platformType()) + "." + (state.bindUserId() == null ? "" : state.bindUserId()),
                STATE_TTL);
    }

    @Override
    public Optional<State> consumeState(String token) {
        String value = consume(STATE_PREFIX, token);
        if (!StringUtils.hasText(value)) return Optional.empty();
        String[] values = value.split("\\.", -1);
        if (values.length != 3) return Optional.empty();
        try {
            return Optional.of(new State(decode(values[0]), decode(values[1]), StringUtils.hasText(values[2]) ? Long.valueOf(values[2]) : null));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void saveBinding(String token, Binding binding) {
        stringRedisTemplate.opsForValue().set(BINDING_PREFIX + token, String.join(".",
                encode(binding.providerCode()), encode(binding.platformType()), encode(binding.socialUid()), encode(binding.nickname()),
                encode(binding.avatarUrl()), encode(binding.gender()), encode(binding.location())), BINDING_TTL);
    }

    @Override
    public Optional<Binding> consumeBinding(String token) {
        String value = consume(BINDING_PREFIX, token);
        if (!StringUtils.hasText(value)) return Optional.empty();
        String[] values = value.split("\\.", -1);
        if (values.length != 7) return Optional.empty();
        try {
            return Optional.of(new Binding(decode(values[0]), decode(values[1]), decode(values[2]), decode(values[3]),
                    decode(values[4]), decode(values[5]), decode(values[6])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String consume(String prefix, String token) {
        return StringUtils.hasText(token) ? stringRedisTemplate.execute(GET_AND_DELETE, Collections.singletonList(prefix + token)) : null;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
