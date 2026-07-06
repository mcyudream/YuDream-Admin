package online.yudream.base.interfaces.platform.docs.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiDocAccessTicketService {

    public static final String COOKIE_NAME = "YDA_API_DOC_ACCESS";
    public static final String PARAM_NAME = "doc_ticket";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> tickets = new ConcurrentHashMap<>();

    public Ticket issue() {
        cleanupExpired();
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expireAt = Instant.now().plus(TTL);
        tickets.put(token, expireAt);
        return new Ticket(token, TTL.toSeconds());
    }

    public boolean valid(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Instant expireAt = tickets.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt.isBefore(Instant.now())) {
            tickets.remove(token);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(now)) {
                iterator.remove();
            }
        }
    }

    public record Ticket(String token, long expiresIn) {
    }
}
