package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived codes intentionally carry no QQ; the message author supplies it at consumption time. */
@Service
public class PluginQqBindingFrameworkService implements PluginQqBindingService {
    private static final Duration TTL = Duration.ofMinutes(15);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public PluginQqBindingCode issue(Long userId) {
        if (userId == null) throw new BizException("用户不能为空");
        Instant expiresAt = Instant.now().plus(TTL);
        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (entries.putIfAbsent(code, new Entry(userId, expiresAt)) != null);
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        return new PluginQqBindingCode(code, expiresAt);
    }

    @Override
    public Long consume(String code) {
        if (code == null || !code.matches("\\d{6}")) throw new BizException("绑定码无效");
        Entry entry = entries.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) throw new BizException("绑定码已过期或已使用");
        return entry.userId();
    }

    private record Entry(Long userId, Instant expiresAt) { }
}
