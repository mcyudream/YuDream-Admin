package online.yudream.base.infra.system.monitor.impl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.system.monitor.dataobj.LoginLogDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SaTokenOnlineUserGateway implements OnlineUserGateway {

    private final UserRepo userRepo;
    private final MongoTemplate mongoTemplate;

    @Override
    public List<OnlineUserDTO> list(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String match = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Set<String> tokens = candidateTokens();
        return tokens.stream()
                .map(this::toOnlineUser)
                .filter(Objects::nonNull)
                .filter(item -> matches(item, match))
                .toList();
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

    private Set<String> candidateTokens() {
        Set<String> tokens = new LinkedHashSet<>(StpUtil.searchTokenValue("", 0, 200, false));
        String currentToken = StpUtil.getTokenValue();
        if (StringUtils.hasText(currentToken)) {
            tokens.add(currentToken);
        }
        return tokens;
    }

    private OnlineUserDTO toOnlineUser(String token) {
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
            Long userId = Long.valueOf(String.valueOf(loginId));
            User user = userRepo.findById(userId).orElse(null);
            return OnlineUserDTO.builder()
                    // 对外仅暴露不可逆会话句柄，原始令牌不出网关。
                    .token(sessionId(token))
                    .userId(userId)
                    .username(user == null ? null : user.getUsername())
                    .nickname(user == null ? null : user.getNickname())
                    .email(user == null || user.getEmail() == null ? null : user.getEmail().getValue())
                    .timeout(timeout)
                    .activeTimeout(StpUtil.getTokenActiveTimeout())
                    .device(StpUtil.getLoginDeviceByToken(token))
                    .build();
        }
        catch (Exception e) {
            return null;
        }
    }

    private boolean matches(OnlineUserDTO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lower = keyword.toLowerCase();
        return contains(item.getToken(), lower)
                || contains(item.getUsername(), lower)
                || contains(item.getNickname(), lower)
                || contains(item.getEmail(), lower)
                || contains(item.getUserId() == null ? null : String.valueOf(item.getUserId()), lower);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
