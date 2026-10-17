package online.yudream.base.infra.system.monitor.impl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sa-Token 会话网关：令牌存储一次枚举上限 {@link #TOKEN_SCAN_LIMIT} 条，
 * 因此分页在枚举结果上切片，而不是把全量丢给前端。
 * 用户信息按「本页命中的 userId」批量取，避免逐条 findById 造成 N+1。
 */
@Service
@RequiredArgsConstructor
public class SaTokenOnlineUserGateway implements OnlineUserGateway {

    /** Sa-Token 令牌检索单次上限，同时是会话总量的上界。 */
    private static final int TOKEN_SCAN_LIMIT = 200;

    private final UserRepo userRepo;

    @Override
    public PageResult<OnlineUserDTO> page(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, TOKEN_SCAN_LIMIT));
        Matched matched = matched(keyword);
        List<Session> sessions = matched.sessions();
        int total = sessions.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        return new PageResult<>(toDtos(sessions.subList(from, to), matched.users()), total, safePage, safeSize);
    }

    @Override
    public List<OnlineUserDTO> list(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, TOKEN_SCAN_LIMIT));
        Matched matched = matched(keyword);
        List<Session> sessions = matched.sessions();
        if (sessions.size() > safeLimit) {
            sessions = sessions.subList(0, safeLimit);
        }
        return toDtos(sessions, matched.users());
    }

    /** 会话句柄：令牌的不可逆摘要，供展示与踢出定位，本身不具备认证价值。 */
    static String sessionId(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("无法计算会话标识", e);
        }
    }

    @Override
    public void kickout(String handle) {
        if (!StringUtils.hasText(handle)) {
            return;
        }
        // 句柄不可逆，踢出前在服务端把句柄解析回真实令牌；兼容直接传入原始令牌的调用。
        for (String candidate : candidateTokens()) {
            if (sessionId(candidate).equals(handle)) {
                StpUtil.kickoutByTokenValue(candidate);
                return;
            }
        }
        StpUtil.kickoutByTokenValue(handle);
    }

    @Override
    public void kickoutByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        // 按登录号注销全部端会话：账号已被删除时，其已发出的令牌必须同步失效。
        StpUtil.logout(userId);
    }

    /** 存活会话，最近活跃优先（剩余有效期越长说明越晚活跃），同值按会话句柄稳定排序。 */
    private List<Session> liveSessions() {
        List<Session> sessions = new ArrayList<>();
        for (String token : candidateTokens()) {
            Session session = liveSession(token);
            if (session != null) {
                sessions.add(session);
            }
        }
        sessions.sort(Comparator.comparingLong(Session::timeout).reversed()
                .thenComparing(session -> sessionId(session.token())));
        return sessions;
    }

    /**
     * 一次查询的中间结果。
     * 关键词过滤必须读到用户名/昵称/邮箱，因此过滤时已批量取过一次用户；
     * 未过滤时 users 为 null，由取数阶段只按本页 userId 批量加载。
     */
    private record Matched(List<Session> sessions, Map<Long, User> users) {
    }

    private Matched matched(String keyword) {
        List<Session> sessions = liveSessions();
        if (!StringUtils.hasText(keyword)) {
            return new Matched(sessions, null);
        }
        Map<Long, User> users = loadUsers(sessions);
        String lower = keyword.trim().toLowerCase();
        List<Session> hits = sessions.stream()
                .filter(session -> matches(session, users.get(session.userId()), lower))
                .toList();
        return new Matched(hits, users);
    }

    private List<OnlineUserDTO> toDtos(List<Session> sessions, Map<Long, User> preloaded) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = preloaded == null ? loadUsers(sessions) : preloaded;
        return sessions.stream().map(session -> toDto(session, users.get(session.userId()))).toList();
    }

    private Map<Long, User> loadUsers(List<Session> sessions) {
        List<Long> userIds = sessions.stream()
                .map(Session::userId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private OnlineUserDTO toDto(Session session, User user) {
        return OnlineUserDTO.builder()
                // 对外仅暴露不可逆会话句柄，原始令牌不出网关。
                .token(sessionId(session.token()))
                .userId(session.userId())
                .username(user == null ? null : user.getUsername())
                .nickname(user == null ? null : user.getNickname())
                .email(user == null || user.getEmail() == null ? null : user.getEmail().getValue())
                .timeout(session.timeout())
                .activeTimeout(session.activeTimeout())
                .device(session.device())
                .build();
    }

    /** 存活会话：有登录态且未过期（永久会话 timeout 为 -1，已过期为 -2）。 */
    private Session liveSession(String token) {
        try {
            Object loginId = StpUtil.getLoginIdByTokenNotThinkFreeze(token);
            if (loginId == null && token.equals(StpUtil.getTokenValue())) {
                loginId = StpUtil.getLoginIdDefaultNull();
            }
            if (loginId == null) {
                return null;
            }
            long timeout = StpUtil.getTokenTimeout(token);
            if (timeout == -2) {
                return null;
            }
            return new Session(token, Long.valueOf(String.valueOf(loginId)), timeout,
                    // 活跃超时按令牌读取，否则每行都会显示成当前请求方的值
                    StpUtil.getStpLogic().getTokenActiveTimeoutByToken(token),
                    StpUtil.getLoginDeviceByToken(token));
        }
        catch (Exception e) {
            return null;
        }
    }

    private Set<String> candidateTokens() {
        Set<String> tokens = new LinkedHashSet<>(StpUtil.searchTokenValue("", 0, TOKEN_SCAN_LIMIT, false));
        String currentToken = StpUtil.getTokenValue();
        if (StringUtils.hasText(currentToken)) {
            tokens.add(currentToken);
        }
        return tokens;
    }

    private boolean matches(Session session, User user, String keyword) {
        return contains(sessionId(session.token()), keyword)
                || contains(session.userId() == null ? null : String.valueOf(session.userId()), keyword)
                || contains(user == null ? null : user.getUsername(), keyword)
                || contains(user == null ? null : user.getNickname(), keyword)
                || contains(user == null || user.getEmail() == null ? null : user.getEmail().getValue(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    /** 单个会话的枚举态信息，用户信息在取数阶段合并。 */
    private record Session(String token, Long userId, long timeout, long activeTimeout, String device) {
    }
}
