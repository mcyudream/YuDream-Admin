package online.yudream.base.interfaces.platform.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AG-UI WebSocket 握手票据：浏览器建立 WebSocket 时无法携带 Authorization 头，
 * 也不应把长效会话令牌放进 URL 查询参数（会进访问日志/历史记录）。
 * 改为「已认证端点签发一次性、60 秒短时效票据」，握手后立即核销。
 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AguiWsTicketService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(String token, Instant expireAt) {
    }

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    /** 为当前登录用户签发一次性握手票据。 */
    public String issue(String token) {
        evictExpired();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(token, Instant.now().plus(TTL)));
        return ticket;
    }

    /** 核销票据并换取签发时绑定的令牌；不存在、过期或已用过的票据返回 null。 */
    public String consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expireAt().isBefore(Instant.now())) {
            return null;
        }
        return entry.token();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expireAt().isBefore(now));
    }
}
