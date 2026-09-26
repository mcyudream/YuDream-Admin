package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件 WebSocket 握手单次票据（浏览器通道：原生 WebSocket 无法携带 Authorization 头）。
 *
 * <p>安全语义：
 * <ul>
 *   <li>票据强绑定（userId、permissions、pluginCode、规范化端点 path）四元组，握手消费时必须全部匹配；</li>
 *   <li>60 秒 TTL，原子 remove 消费，单次有效；</li>
 *   <li>活跃票据总量与按用户发放速率受限，防止无界内存与爆破；</li>
 *   <li>token 绝不进入日志：记录对象 toString 均脱敏。</li>
 * </ul>
 * 票据仅缓解浏览器 WS 无凭据通道的问题，授权判定仍在握手阶段按实时权限完成。
 */
@Service
public class PluginWsTicketService {

    /** 握手查询参数名。 */
    public static final String PARAM_NAME = "ws_ticket";

    static final Duration TTL = Duration.ofSeconds(60);
    static final int MAX_LIVE_TICKETS = 1024;
    static final int MAX_ISSUE_PER_USER_PER_WINDOW = 30;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Instant>> userIssueTimes = new ConcurrentHashMap<>();

    /** 发放票据；userId/pluginCode/endpointPath 必填，permissions 为发放时的主体权限（消费后仍会实时复查）。 */
    public IssuedTicket issue(Long userId, List<String> permissions, String pluginCode, String endpointPath) {
        if (userId == null) {
            throw new BizException("WebSocket 票据仅对已认证用户发放");
        }
        validateBinding(pluginCode, endpointPath);
        cleanupExpired();
        if (tickets.size() >= MAX_LIVE_TICKETS) {
            throw new BizException("WebSocket 票据发放繁忙，请稍后重试");
        }
        throttle(userId);
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(token, userId, permissions, pluginCode.trim(), normalize(endpointPath)));
        return new IssuedTicket(token, TTL.toSeconds());
    }

    /** 原子消费：任何不匹配（过期/重复/code/path 不一致）都视为无效并移除。 */
    public Optional<ConsumedTicket> consume(String token, String pluginCode, String endpointPath) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(pluginCode) || !StringUtils.hasText(endpointPath)) {
            return Optional.empty();
        }
        Ticket ticket = tickets.remove(token.trim());
        if (ticket == null || ticket.expireAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!ticket.pluginCode().equals(pluginCode.trim()) || !ticket.endpointPath().equals(normalize(endpointPath))) {
            return Optional.empty();
        }
        return Optional.of(new ConsumedTicket(ticket.userId(), ticket.permissions()));
    }

    /** 活跃票据数（运维/测试观察用）。 */
    public int liveTickets() {
        cleanupExpired();
        return tickets.size();
    }

    private void throttle(Long userId) {
        Instant now = Instant.now();
        Deque<Instant> window = userIssueTimes.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().plus(TTL).isBefore(now)) {
                window.pollFirst();
            }
            if (window.size() >= MAX_ISSUE_PER_USER_PER_WINDOW) {
                throw new BizException("WebSocket 票据发放过于频繁，请稍后重试");
            }
            window.addLast(now);
        }
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Ticket>> iterator = tickets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expireAt().isBefore(now)) {
                iterator.remove();
            }
        }
        if (userIssueTimes.size() > MAX_LIVE_TICKETS) {
            userIssueTimes.entrySet().removeIf(entry -> {
                Deque<Instant> window = entry.getValue();
                synchronized (window) {
                    window.removeIf(instant -> instant.plus(TTL).isBefore(now));
                    return window.isEmpty();
                }
            });
        }
    }

    /**
     * 票据绑定校验：code 限定 {@code [A-Za-z0-9][A-Za-z0-9._-]*}（不含斜杠/查询字符）；
     * 端点路径 ≤2048 字符且不含 '%'、空白与控制字符、不含 '?'/'#'。
     * 路径一律按原始（未解码）语义比对，不做 URL 解码，显式拒绝编码与二次编码形态。
     */
    static void validateBinding(String pluginCode, String endpointPath) {
        if (!StringUtils.hasText(pluginCode)) {
            throw new BizException("WebSocket 票据必须绑定插件 code");
        }
        if (!pluginCode.trim().matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new BizException("插件 code 含非法字符");
        }
        if (!StringUtils.hasText(endpointPath)) {
            throw new BizException("WebSocket 票据必须绑定端点路径");
        }
        if (endpointPath.length() > PluginWsPaths.MAX_ENDPOINT_PATH_LENGTH) {
            throw new BizException("端点路径过长（上限 " + PluginWsPaths.MAX_ENDPOINT_PATH_LENGTH + " 字符）");
        }
        for (int index = 0; index < endpointPath.length(); index++) {
            char ch = endpointPath.charAt(index);
            if (ch == '%' || ch == '?' || ch == '#' || Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                throw new BizException("端点路径含非法字符（不支持编码/查询/空白字符）");
            }
        }
    }

    /** 与 PluginContextImpl 注册路径同一规范化：trim 后保证以 "/" 开头。 */
    static String normalize(String path) {
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /** 对外发放结果。toString 脱敏，防止 token 进入日志。 */
    public record IssuedTicket(String token, long expiresIn) {
        @Override
        public String toString() {
            return "IssuedTicket[token=<redacted>, expiresIn=" + expiresIn + "]";
        }
    }

    private record Ticket(String token, Long userId, List<String> permissions, String pluginCode,
                          String endpointPath, Instant expireAt) {
        private Ticket(String token, Long userId, List<String> permissions, String pluginCode, String endpointPath) {
            this(token, userId,
                    permissions == null ? List.of() : List.copyOf(permissions),
                    pluginCode, endpointPath, Instant.now().plus(TTL));
        }

        @Override
        public String toString() {
            return "Ticket[token=<redacted>, userId=" + userId + ", pluginCode=" + pluginCode
                    + ", endpointPath=" + endpointPath + "]";
        }
    }

    /** 消费成功后的主体快照（权限仅用于展示，握手会按 userId 实时重查）。 */
    public record ConsumedTicket(Long userId, List<String> permissions) {
        public ConsumedTicket {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }
}
